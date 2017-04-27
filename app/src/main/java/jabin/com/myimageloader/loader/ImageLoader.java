package jabin.com.myimageloader.loader;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.support.annotation.NonNull;
import android.support.v4.util.LruCache;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author :wangjm1
 * @version :1.0
 * @package : jabin.com.myimageloader.loader
 * @class : ${CLASS_NAME}
 * @time : 2017/4/27 ${ITME}
 * @description :TODO
 */
public class ImageLoader {

    private static final String TAG = "ImageLoader";
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = CPU_COUNT + 1;
    private static final int MAX_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final long KEEPALIVE = 10L;

    private static final long DISK_CACHE_SIZE = 1024 * 1024 * 50;

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new ThreadFactory() {
        private final AtomicInteger number = new AtomicInteger(1);

        @Override
        public Thread newThread(@NonNull Runnable runnable) {
            return new Thread(runnable, "MyImageLoader#" + number.getAndIncrement());
        }
    };
    private static final Executor DEFAULT_THREAD_POOL_EXECUTOR =
        new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEPALIVE,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(),
            DEFAULT_THREAD_FACTORY
        );

    private Context mContext;
    private LruCache<String, Bitmap> mCache;
    private DiskLruCache mDiskCache;
    private boolean isCreateDiskCacheSucc;

    public ImageLoader(Context context) {
        context = context.getApplicationContext();
        mContext = context;
        int maxMemory = (int) Runtime.getRuntime().maxMemory() / 1024;
        int cacheSize = maxMemory / 8;
        mCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight() / 1024;
            }
        };
        File diskCacheDir = getDiskCacheDir(mContext, "bitmapcache");
        if (!diskCacheDir.exists()) {
            diskCacheDir.mkdir();
        }

        if (getUsableSpace(diskCacheDir) > DISK_CACHE_SIZE) {
            try {
                mDiskCache = DiskLruCache.open(diskCacheDir, 1, 1, DISK_CACHE_SIZE);
                isCreateDiskCacheSucc = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private long getUsableSpace(File diskCacheDir) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            return diskCacheDir.getUsableSpace();
        }
        final StatFs stats = new StatFs(diskCacheDir.getPath());
        return stats.getBlockSize() * stats.getAvailableBlocks();
    }

    private File getDiskCacheDir(Context context, String name) {
        boolean hasExternalStorageAva = Environment.getExternalStorageState().
            equals(Environment.MEDIA_MOUNTED);
        final String cachePath;
        if (hasExternalStorageAva) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + name);
    }


    /**
     * 图片压缩
     */
    class ImageResize {

        public Bitmap decodeSampledBitmapformResource(Resources res,
                                                      int resId,
                                                      int reqWidth,
                                                      int reqHeight) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(res, resId, options);

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeResource(res, resId, options);
        }

        private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
            if (reqWidth == 0 || reqHeight == 0) {
                return 1;
            }

            final int rawWidth = options.outWidth;
            final int rawHeight = options.outHeight;
            int sampleSize = 1;
            if (rawWidth > reqWidth || rawHeight > reqHeight) {
                int halfWidth = rawWidth / 2;
                int halfHeight = rawHeight / 2;
                while ((halfHeight / sampleSize) >= reqHeight && (halfWidth / sampleSize) >= reqWidth) {
                    sampleSize = sampleSize << 1;
                }
            }
            return sampleSize;

        }


    }
}
