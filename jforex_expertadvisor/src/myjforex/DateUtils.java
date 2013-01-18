package myjforex;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 日付系
 */
public class DateUtils {

	/** yyyy/MM/dd HH:mm:ss SSS */
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss SSS");

	public static String longToString(Long time) {
		if (time == null) {
			return "";
		}
		return DATE_FORMAT.format(new Date(time));
	}

	/**
	 * 1970/01/01 09:00:00 000 の場合に空文字を返す
	 * 
	 * @param timeLong
	 * @return
	 * @throws ParseException
	 */
	public static String longToStringZeroForEmpty(Long timeLong) {
		final Long startTime = StringToLong("1970/01/01 09:00:00 000");
		if (startTime == timeLong) {
			return "";
		}
		return longToString(timeLong);
	}

	/**
	 * @param date {@link DateUtils#DATE_FORMAT}
	 * @return
	 * @throws ParseException
	 */
	public static Long StringToLong(String date) {
		try {
			return DATE_FORMAT.parse(date).getTime();
		} catch (ParseException e) {
			e.printStackTrace();
			return StringToLong("1970/01/01 09:00:00 000");
		}
	}

}
