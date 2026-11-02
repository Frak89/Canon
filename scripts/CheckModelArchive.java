import com.ampyeos.canon.ModelArchive;
import java.io.File;

/**
 * Runs the same archive parser as Android on the real official .tgz during CI.
 * CI validates extracted SHA256 and exact byte comparison with GNU tar.
 */
public final class CheckModelArchive {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("archive and destination required");
        long bytes = ModelArchive.extract(new File(args[0]),new File(args[1]),
            "mobilenet_v2_1.0_224.tflite",256L*1024*1024);
        System.out.println("Android ModelArchive: extracted the TFLite after scanning "+
            bytes+" bytes of TAR data (other entries streamed safely)");
    }
}
