package bench;
import conv.RowParallel;
import conv.ColParallel;
import conv.GridParallel;
import conv.PixelParallel;
import conv.Sequential;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.opencv.core.*;
import java.util.*;
import kotlin.Pair;
import filters.Filter;
import static filters.FilterExtKt.toCvKernel;
import static filters.FiltersKt.getFilterList;



@State(Scope.Benchmark)
public class ConvBench {
    @Param({"row","grid","seq","pix","col"})
    public String mode;


    @Param({"1024"})
    public int size; // image is size x size


    @Param({"gaussian_blur_3x3"})
    public String filterName;

    @Param({"64","128","256"})
    public int blockSize;

    @Param({"1","2","4","8"})
    public int xWorkers;

    private Mat gray;
    private Mat kernel;

    private static void loadOpenCv() {
        try {
            Class<?> c = Class.forName("nu.pattern.OpenCV");
            c.getMethod("loadLocally").invoke(null);
        } catch (Throwable t) {
            System.loadLibrary(org.opencv.core.Core.NATIVE_LIBRARY_NAME);
        }
    }
    @Setup(Level.Trial)
    public void setup() {
        loadOpenCv();
        gray = new Mat(size, size, CvType.CV_8UC1);
        byte[] buf = new byte[size * size];
        new Random(42).nextBytes(buf);
// put data row-by-row
        int idx = 0;
        for (int r = 0; r < size; r++) {
            gray.put(r, 0, Arrays.copyOfRange(buf, idx, idx + size));
            idx += size;
        }
// pick kernel from your filters list
        for (Pair<String, Filter> p : getFilterList()) {
            if (p.getFirst().equals(filterName)) {
                kernel = toCvKernel(p.getSecond());
                break;
            }
        }
        if (kernel == null) kernel = toCvKernel(getFilterList().get(0).getSecond());
    }


    @TearDown(Level.Trial)
    public void tearDown() {
        if (gray != null) gray.release();
        if (kernel != null) kernel.release();
    }


    @Benchmark
    public void bench(Blackhole bh) {
        Mat res;
        switch (mode) {
            case "row": res = RowParallel.INSTANCE.apply(gray, kernel); break;
            case "col": res = ColParallel.INSTANCE.apply(gray, kernel); break;
            case "grid": res = GridParallel.INSTANCE.apply(gray, kernel, blockSize, xWorkers); break;
            case "pix": res = PixelParallel.INSTANCE.apply(gray, kernel); break;
            case "seq": res = Sequential.INSTANCE.apply(gray, kernel); break;
            default: res = RowParallel.INSTANCE.apply(gray, kernel);
        }
        bh.consume(res);
        res.release();
    }
}