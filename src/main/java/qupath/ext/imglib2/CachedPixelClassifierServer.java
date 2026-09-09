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

/**
 * A {@link PixelClassificationImageServer} that caches predictions to an OME.Zarr file in the project entry directory or in a temporary file.
 */
public class CachedPixelClassifierServer extends PixelClassificationImageServer {

    private final ImgLib2ImageServer<?> omeServer;

    /**
     * Create a cached pixel classifier server in the project image entry directory named according to the pixel classifier name (should be consistent across sessions).
     * @param project the QuPath project
     * @param imageData the image
     * @param pixelClassifierName the name of the pixel classifier
     * @throws IOException if caching or reading the cache fails
     */
    public CachedPixelClassifierServer(Project<BufferedImage> project, ImageData<BufferedImage> imageData, String pixelClassifierName) throws IOException {
        this(project.getEntry(imageData).getEntryPath().resolve("cache").resolve(pixelClassifierName + ".ome.zarr"),
                imageData,
                project.getPixelClassifiers().get(pixelClassifierName));
    }

    /**
     * Create a cached pixel classifier server in the project entry directory named according to the pixel classifier's hashcode (not persistent across sessions).
     * @param projectImageEntry
     * @param pixelClassifier
     * @throws IOException
     */
    public CachedPixelClassifierServer(ProjectImageEntry<BufferedImage> projectImageEntry, PixelClassifier pixelClassifier) throws IOException {
        this(projectImageEntry.getEntryPath().resolve("cache").resolve(pixelClassifier.hashCode() + ".ome.zarr"),
                projectImageEntry.readImageData(),
                pixelClassifier);
    }

    /**
     * Create a cached pixel classifier server
     * @param cachePath where to cache the predictions
     * @param imageData the image
     * @param pixelClassifier the pixel classifier object
     */
    public CachedPixelClassifierServer(Path cachePath, ImageData<BufferedImage> imageData, PixelClassifier pixelClassifier) {
        super(imageData, pixelClassifier);
        try {
            if (!isZarr(cachePath.toUri())) {
                var pozw = new PyramidalOMEZarrWriter.Builder(this)
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
