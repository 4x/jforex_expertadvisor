package singlejartest;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class TestHoge {

	public static void main(String[] args) throws ParseException {

		String dateString = "2010/01/01 00:00:00 000";

		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss SSS");

		// Calendarに日時を設定し、getTimeでDateを生成すると設定した日時と1ヶ月ずれる
		Calendar calendar = Calendar.getInstance();
		calendar.set(2010, 3, 1);
		System.out.println(dateFormat.format(new Date(calendar.getTime().getTime())));

		// この方法ならずれない
		Date date = dateFormat.parse(dateString);
		// 日本標準時
		dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));
		System.out.println("日本標準時: " + dateFormat.format(date) + ":" + dateFormat.parse(dateString).getTime());
		// 太平洋標準時
		dateFormat.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"));
		System.out.println("太平洋標準時: " + dateFormat.format(date) + ":" + dateFormat.parse(dateString).getTime());
		// 中部標準時
		dateFormat.setTimeZone(TimeZone.getTimeZone("America/Belize"));
		System.out.println("中部標準時: " + dateFormat.format(date) + ":" + dateFormat.parse(dateString).getTime());
		// 東部標準時
		dateFormat.setTimeZone(TimeZone.getTimeZone("America/New_York"));
		System.out.println("東部標準時: " + dateFormat.format(date) + ":" + dateFormat.parse(dateString).getTime());
		// グリニッジ標準時
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		System.out.println("グリニッジ標準時: " + dateFormat.format(date) + ":" + dateFormat.parse(dateString).getTime());
		// 中部ヨーロッパ時間
		dateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
		System.out.println("中部ヨーロッパ時間: " + dateFormat.format(date) + ":" + dateFormat.parse(dateString).getTime());
	}
}
