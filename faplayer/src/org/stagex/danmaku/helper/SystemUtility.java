package org.stagex.danmaku.helper;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;

import org.stagex.danmaku.R;

import android.os.Build;
import android.os.Environment;

public class SystemUtility {

	static {
		System.loadLibrary("vlccore");
	}

	public static native int setenv(String name, String value, boolean overwrite);

	private static int mArmArchitecture = -1;

	public static int getArmArchitecture() {
		if (mArmArchitecture != -1)
			return mArmArchitecture;
		try {
			InputStream is = new FileInputStream("/proc/cpuinfo");
			InputStreamReader ir = new InputStreamReader(is);
			BufferedReader br = new BufferedReader(ir);
			try {
				String name = "CPU architecture";
				while (true) {
					String line = br.readLine();
					String[] pair = line.split(":");
					if (pair.length != 2)
						continue;
					String key = pair[0].trim();
					String val = pair[1].trim();
					if (key.compareToIgnoreCase(name) == 0) {
						String n = val.substring(0, 1);
						mArmArchitecture = Integer.parseInt(n);
						break;
					}
				}
			} finally {
				br.close();
				ir.close();
				is.close();
				if (mArmArchitecture == -1)
					mArmArchitecture = 6;
			}
		} catch (Exception e) {
			mArmArchitecture = 6;
		}
		return mArmArchitecture;
	}

	public static int getSDKVersionCode() {
		// TODO: fix this
		return Build.VERSION.SDK_INT;
	}

	public static String getExternalStoragePath() {
		boolean exists = Environment.getExternalStorageState().equals(
				android.os.Environment.MEDIA_MOUNTED);
		if (exists)
			return Environment.getExternalStorageDirectory().getAbsolutePath();
		else
			return "/";
	}

	@SuppressWarnings("rawtypes")
	public static int getDrawableId(String name) {
		int result = -1;
		try {
			Class clz = Class.forName(R.drawable.class.getName());
			Field field = clz.getField(name);
			result = field.getInt(new R.drawable());
		} catch (Exception e) {
		}
		return result;
	}

	@SuppressWarnings("rawtypes")
	public static Object realloc(Object oldArray, int newSize) {
		int oldSize = java.lang.reflect.Array.getLength(oldArray);
		Class elementType = oldArray.getClass().getComponentType();
		Object newArray = java.lang.reflect.Array.newInstance(elementType,
				newSize);
		int preserveLength = Math.min(oldSize, newSize);
		if (preserveLength > 0)
			System.arraycopy(oldArray, 0, newArray, 0, preserveLength);
		return newArray;
	}

	public static String getTimeString(int msec) {
		if (msec < 0) {
			return String.format("--:--:--");
		}
		int total = msec / 1000;
		int hour = total / 3600;
		total = total % 3600;
		int minute = total / 60;
		int second = total % 60;
		return String.format("%02d:%02d:%02d", hour, minute, second);
	}

}
