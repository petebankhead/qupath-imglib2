package qupath.ext.imglib2;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import qupath.ext.zarr.OmeZarrImageServerBuilder;
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.images.writers.ome.zarr.PyramidalOMEZarrWriter;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;


import static ome.zarr.imglib2.ZarrUtils.isZarr;

public class CachedPixelClassifierServer extends PixelClassificationImageServer {

    private final ImgLib2ImageServer<?> omeServer;

    public CachedPixelClassifierServer(Project<BufferedImage> project, ImageData<BufferedImage> imageData, String pixelClassifierName) throws IOException {
        this(project.getEntry(imageData).getEntryPath().resolve("cache").resolve(pixelClassifierName + ".ome.zarr"),
                imageData,
                project.getPixelClassifiers().get(pixelClassifierName));
    }

    public CachedPixelClassifierServer(ProjectImageEntry<BufferedImage> projectImageEntry, PixelClassifier pixelClassifier) throws IOException {
        this(projectImageEntry.getEntryPath().resolve("cache").resolve(pixelClassifier.hashCode() + ".ome.zarr"),
                projectImageEntry.readImageData(),
                pixelClassifier);
    }

    public CachedPixelClassifierServer(Path cachePath, ImageData<BufferedImage> imageData, PixelClassifier pixelClassifier) {
        super(imageData, pixelClassifier);
        try {
            if (!isZarr(cachePath.toUri())) {
                var pozw = new PyramidalOMEZarrWriter.Builder(this)
                        .downsamples(2, 4, 8)
                        .build(cachePath.toString());
                pozw.writeImage();
            }
            this.omeServer = (ImgLib2ImageServer<?>) new OmeZarrImageServerBuilder().buildServer(cachePath.toUri());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public CachedPixelClassifierServer(ImageData<BufferedImage> imageData, PixelClassifier classifier) throws IOException {
        this(Files.createTempFile("qupath_", ".ome.zarr"), imageData, classifier);
    }

    @Override
    protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
        if (omeServer != null) {
            return omeServer.readTile(tileRequest);
        }
        return super.readTile(tileRequest);
    }

}
