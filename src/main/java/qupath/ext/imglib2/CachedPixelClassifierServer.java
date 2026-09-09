package qupath.ext.imglib2;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import qupath.ext.zarr.OmeZarrImageServerBuilder;
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.images.writers.ome.zarr.PyramidalOMEZarrWriter;

public class CachedPixelClassifierServer extends PixelClassificationImageServer {

    private final String cachePath;
    private final ImgLib2ImageServer<?> omeServer;

    public CachedPixelClassifierServer(ImageData<BufferedImage> imageData, PixelClassifier classifier) {
        super(imageData, classifier);
        try {
            cachePath = "/tmp/cached.ome.zarr";
            var pozw = new PyramidalOMEZarrWriter.Builder(this)
                    .downsamples(2, 4, 8)
                    .build(cachePath);
            pozw.writeImage();
            this.omeServer = (ImgLib2ImageServer<?>) new OmeZarrImageServerBuilder().buildServer(Path.of(cachePath).toUri());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
        if (omeServer != null) {
            return omeServer.readTile(tileRequest);
        }
        return super.readTile(tileRequest);
    }

}
