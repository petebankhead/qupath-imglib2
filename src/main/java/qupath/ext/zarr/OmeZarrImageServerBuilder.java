package qupath.ext.zarr;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;
import ome.zarr.imglib2.PyramidBackend;
import ome.zarr.imglib2.PyramidContents;
import ome.zarr.imglib2.ZarrUtils;
import ome.zarr.imglib2.metadata.AxisCalibration;
import ome.zarr.imglib2.metadata.Omero;
import ome.zarr.zarrjava.ZarrJavaPyramidBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.imglib2.ImgBuilder;
import qupath.ext.imglib2.ImgLib2ImageServer;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.PixelCalibration;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class OmeZarrImageServerBuilder implements ImageServerBuilder<BufferedImage> {

    private static final Logger logger = LoggerFactory.getLogger(OmeZarrImageServerBuilder.class);

    @Override
    public UriImageSupport<BufferedImage> checkImageSupport(URI uri, String... args) throws IOException {
        float supportLevel = 0f;
        if (ZarrUtils.isZarr(uri))
            supportLevel = 5f;
        return UriImageSupport.createInstance(
                OmeZarrImageServerBuilder.class,
                supportLevel,
                DefaultImageServerBuilder.createInstance(
                        OmeZarrImageServerBuilder.class, uri, args
                ));
    }

    @Override
    public ImageServer<BufferedImage> buildServer(URI uri, String... args) throws Exception {
        return create(uri, args);
    }

    private < T extends NativeType< T > & RealType< T >> ImageServer<BufferedImage> create(URI uri, String... args) throws Exception {
        PyramidBackend backend = new ZarrJavaPyramidBackend();
        PyramidContents<T> contents = backend.read(uri);
        List<RandomAccessibleInterval<T>> resolutions = new ArrayList<>();
        AxisCalibration[] axes = contents.axesPerLevel[0];
        PixelCalibration cal = parsePixelCalibration(axes);
        for (int r = 0; r < contents.numResolutionLevels(); r++) {
            RandomAccessibleInterval<T> img = contents.asImg(r);
            if (img.numDimensions() > 5 || img.numDimensions() < 2) {
                throw new IOException("Invalid image dimensions: " + img.numDimensions());
            }
            // Switch XYCZT to XYZCT
            if (img.numDimensions() > 3) {
                img = Views.moveAxis(img, 2, 3);
            }
            // Ensure we have enough dimensions
            while (img.numDimensions() < 5) {
                img = Views.addDimension(img, 0, 0);
            }
            resolutions.add(img);
        }
        var builder = ImgLib2ImageServer.builder(resolutions);
        var channels = parseChannels(contents.omero);
        long expectedChannels = resolutions.getFirst().dimension(ImgBuilder.AXIS_CHANNEL);
        if (channels.size() == expectedChannels) {
            builder = builder.channels(channels);
        } else if (!channels.isEmpty()) {
            logger.warn("Expected {} channels, found {}", expectedChannels, channels.size());
        }
        return builder
                .pixelCalibration(cal)
                .build();
    }

    private static PixelCalibration parsePixelCalibration(AxisCalibration[] axes) {
        if (!Objects.equals(AxisCalibration.X, axes[0].name))
            throw new IllegalArgumentException("Expected first axis to be " + AxisCalibration.X + ", found " + axes[0].name);
        if (!Objects.equals(AxisCalibration.Y, axes[1].name))
            throw new IllegalArgumentException("Expected first axis to be " + AxisCalibration.Y + ", found " + axes[1].name);
        // Original expected order is XYZCT *before* we convert for QuPath
        var builder = new PixelCalibration.Builder();
        if (isMicrons(axes[0].unit) && isMicrons(axes[1].unit)) {
            builder = builder.pixelSizeMicrons(axes[0].scale, axes[1].scale);
        }
        if (axes.length > 3) {
            if (!Objects.equals(AxisCalibration.Z, axes[2].name))
                throw new IllegalArgumentException("Expected first axis to be " + AxisCalibration.Z + ", found " + axes[2].name);
            if (isMicrons(axes[2].unit)) {
                builder = builder.zSpacingMicrons(axes[2].scale);
            }
        }
        return builder.build();
    }

    private static final Set<String> MICRONS = Set.of(
            "µm", "um",
            "micron", "microns",
            "micrometre", "micrometres",
            "micrometer", "micrometers");

    private static boolean isMicrons(String unit) {
        return MICRONS.contains(unit);
    }

    private static List<ImageChannel> parseChannels(Omero omero) {
        List<ImageChannel> channels = new ArrayList<>();
        if (omero.channels == null) {
            return channels;
        }
        for (int c = 0; c < omero.channels.size(); c++) {
            var channel = omero.channels.get(c);
            String name = channel.label;
            Integer color = parseColor(channel.color).orElse(ImageChannel.getDefaultChannelColor(c));
            channels.add(ImageChannel.getInstance(name, color));
        }
        return channels;
    }

    private static Optional<Integer> parseColor(String color) {
        if (color != null && !color.isBlank()) {
            try {
                if (!color.startsWith("#")) {
                    color = "#" + color;
                }
                return Optional.of(
                        Color.decode(color).getRGB()
                );
            } catch (Exception e) {
                logger.warn("Invalid image color: {}", color);
            }
        }
        return Optional.empty();
    }

    @Override
    public String getName() {
        return "OME-Zarr server builder";
    }

    @Override
    public String getDescription() {
        return "Image server builder to support OME-Zarr";
    }

    @Override
    public Class<BufferedImage> getImageType() {
        return BufferedImage.class;
    }

}
