package ak.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.os.Build;
import android.util.Log;

public final class NativeUtils {
	private static final String TAG = "AKUtils";
	
	private static ArrayList<ZipEntry> selectAbi(final String arch,
			final ArrayList<ZipEntry> armeabi,
			final ArrayList<ZipEntry> armeabi_v7a,
			final ArrayList<ZipEntry> arm64_v8a, final ArrayList<ZipEntry> x86,
			final ArrayList<ZipEntry> x86_64) {
		switch (arch) {
		case "armeabi-v7a":
		case "armeabi":
			if (armeabi_v7a.size() > 0)
				return armeabi_v7a;
			else if (armeabi.size() > 0)
				return armeabi;
			break;
		case "arm64-v8a":
			if (arm64_v8a.size() > 0)
				return arm64_v8a;
			break;
		case "x86":
			if (x86.size() > 0)
				return x86;
		case "x86_64":
			if (x86_64.size() > 0)
				return x86_64;
		}
		return null;
	}

    /**
     * Copies native binaries to a shared library directory.
     *
     * @param path APK file to scan for native libraries
     * @param sharedLibraryDir directory for libraries to be copied to
     */
	@SuppressWarnings("deprecation")
	public static void copyNativeBinaries(final String path, final File sharedLibraryDir) {
		final ArrayList<ZipEntry> armeabi = new ArrayList<ZipEntry>();
		final ArrayList<ZipEntry> armeabi_v7a = new ArrayList<ZipEntry>();
		final ArrayList<ZipEntry> arm64_v8a = new ArrayList<ZipEntry>();
		final ArrayList<ZipEntry> x86 = new ArrayList<ZipEntry>();
		final ArrayList<ZipEntry> x86_64 = new ArrayList<ZipEntry>();
		try {
			final ZipFile zf = new ZipFile(path);
			final Enumeration<? extends ZipEntry> es = zf.entries();
			boolean hasLibraries = false;
			while (es.hasMoreElements()) {
				final ZipEntry e = es.nextElement();
				final long s = e.getSize();
				if (s > 0) {
					// lib/armeabi/libAK.so
					final String en = e.getName();
					if (en.startsWith("lib/armeabi-v7a/")) {
						armeabi_v7a.add(e);
						hasLibraries = true;
					} else if (en.startsWith("lib/armeabi/")) {
						armeabi.add(e);
						hasLibraries = true;
					} else if (en.startsWith("lib/arm64-v8a/")) {
						arm64_v8a.add(e);
						hasLibraries = true;
					} else if (en.startsWith("lib/x86/")) {
						x86.add(e);
						hasLibraries = true;
					} else if (en.startsWith("lib/x86_64/")) {
						x86_64.add(e);
						hasLibraries = true;
					}
				}
			}
			if (!hasLibraries) {
				zf.close();
				return;
			}

			ArrayList<ZipEntry> abi = selectAbi(Build.CPU_ABI, armeabi,
					armeabi_v7a, arm64_v8a, x86, x86_64);
			if (abi == null) {
				Log.w(TAG, Build.CPU_ABI + " abi not found or incompatible, trying " + Build.CPU_ABI2 + "...");
				abi = selectAbi(Build.CPU_ABI2, armeabi, armeabi_v7a,
						arm64_v8a, x86, x86_64);
			}
			if (abi == null) {
				Log.e(TAG, "Not compatible abi found at" + path);
				zf.close();
				return;
			}
			
			sharedLibraryDir.mkdirs();
			Log.v(TAG, "extracting libraries to " + sharedLibraryDir.getAbsolutePath() + "...");

			final byte[] buffer = new byte[1024 * 512];
			for (final ZipEntry e : abi) {
				final File f = new File(sharedLibraryDir, new File(e.getName()).getName());
				final long s = f.length();
				if (s > 0 && s == e.getSize()) {
					Log.w(TAG, "Ignored unchanged file " + f.getCanonicalPath());
					continue;
				}

				try {
					final InputStream src = zf.getInputStream(e);
					final FileOutputStream dest = new FileOutputStream(f);
					int len;
					while ((len = src.read(buffer)) > 0) {
						dest.write(buffer, 0, len);
					}
					dest.close();
					src.close();
				} catch (final Exception ex) {
					Log.e(TAG, f.getCanonicalPath(), ex);
					break;
				}

				f.setExecutable(true);
			}
			
			zf.close();
		} catch (final Exception e) {
			Log.wtf(TAG, e);
		}
	}
}
