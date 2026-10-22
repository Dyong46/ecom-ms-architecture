package com.duong.module.common.util;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DateUtils {
	public static final String UTC_TIME_ZONE = "UTC";
	public static final String LOCAL_TIME_ZONE = "Asia/Ho_Chi_Minh";
	public static final String T_MEDIUM_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";
	public static final String MEDIUM_PATTERN = "yyyy-MM-dd HH:mm:ss";
	public static final String yyyyMMddHHmmss = "yyyyMMddHHmmss";
	public static final String SHORT_PATTERN = "dd-MM-yyyy";
	public static final String yyyyMM = "yyyyMM";
	public static final String YYYY_MM_DD_SLASH = "yyyy/MM/dd";
	public static final int NUMBER_DAYS_OF_WEEK = 7;
	public static final int NUMBER_WEEKS_OF_MONTH = 4;
	public static final int NUMBER_DAYS_OF_MONTH = 30;
	public static final String FULL_TIME_PATTERN = "HH:mm:ss";
	public static final String HOUR_SHORT_PATTERN = "HH:mm";
	public static final Long DAY_TIME_MILLI = 24 * 3600 * 1000L;
	public static final String yyyyMMdd = "yyyyMMdd";
	public static final String MATH_TOURNAMENT_DATE_PATTERN = "MM/dd/yyyy";
	public static final String SQL_DATE_PATTERN = "yyyy-MM-dd";
	public static final String DD_MM_YYYY_SLASH = "dd/MM/yyyy";
  public static final String NOTIFICATION_TIME_PATTERN = "HH:mm MM/dd/yyyy";

	public static Timestamp parse(String source) throws ParseException {
		SimpleDateFormat dateFormat = new SimpleDateFormat(MEDIUM_PATTERN);
		Date parsedDate = dateFormat.parse(source);

		return new Timestamp(parsedDate.getTime());
	}

	/**
	 * @param original
	 * @param sec
	 * @return
	 */
	public static Timestamp addSecondToTimestamp(Timestamp original, Integer sec) {
		Calendar cal = Calendar.getInstance();

		cal.setTimeInMillis(original.getTime());
		cal.add(Calendar.SECOND, sec);
		return new Timestamp(cal.getTime().getTime());
	}

	/**
	 * @param original
	 * @param seconds
	 * @return
	 */
	public static Timestamp subtractSecondsFromTimestamp(Timestamp original, Integer seconds) {
		return addSecondToTimestamp(original, -seconds);
	}

	/**
	 * @param original
	 * @param arrSec
	 * @return
	 */
	public static Timestamp addSecondToTimestamp(Timestamp original, Integer... arrSec) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(original.getTime());
		for (Integer sec : arrSec) {
			cal.add(Calendar.SECOND, sec);
		}
		return new Timestamp(cal.getTime().getTime());
	}

	/**
	 * @param source
	 * @param pattern
	 * @return
	 * @throws ParseException
	 */
	public static String dateToString(Date date) {
		try {
			SimpleDateFormat dateFormat = new SimpleDateFormat(MEDIUM_PATTERN);
			return dateFormat.format(date);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * @param source
	 * @param pattern
	 * @return
	 * @throws ParseException
	 */
	public static String dateToString(Date date, String pattern) throws ParseException {
		SimpleDateFormat dateFormat = new SimpleDateFormat(pattern);
		return dateFormat.format(date);
	}

}
