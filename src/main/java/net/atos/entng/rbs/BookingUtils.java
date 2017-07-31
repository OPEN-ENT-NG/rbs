/*
 * Copyright © Région Nord Pas de Calais-Picardie,  Département 91, Région Aquitaine-Limousin-Poitou-Charentes, 2016.
 *
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package net.atos.entng.rbs;

import net.atos.entng.rbs.service.BookingServiceSqlImpl;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserInfos.Function;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class BookingUtils {
	private final static String DATE_FORMAT = BookingServiceSqlImpl.DATE_FORMAT;
	public static final String TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";


	/**
	 * @param lastSelectedDay : index of last selected day
	 * @param selectedDays    : bit string, used like an array of boolean, representing selected days. Char 0 is sunday, char 1 monday...
	 * @param periodicity     : slots are repeated every "periodicity" week
	 * @return Number of days until next slot
	 * @throws IllegalArgumentException, IndexOutOfBoundsException
	 */
	public static int getNextInterval(final int lastSelectedDay, final String selectedDays, final int periodicity) {
		if (!selectedDays.contains("1")) {
			throw new IllegalArgumentException("Argument selectedDays must contain char '1'");
		}

		int count = 0;
		int k = lastSelectedDay;

		do {
			k++;
			if (k == 1) {
				// On monday, go to next week
				count += (periodicity - 1) * 7;
			} else if (k >= 7) {
				k = k % 7;
			}
			count++;
		} while (selectedDays.charAt(k) != '1');

		return count;
	}

	/**
	 * @param firstSelectedDay : index of the first slot's day
	 * @param selectedDays     : bit string, used like an array of boolean, representing selected days. Char 0 is sunday, char 1 monday...
	 * @param durationInDays   : difference in days between the first slot's end date and the end date of the periodic booking
	 * @param periodicity      : slots are repeated every "periodicity" week
	 * @return Number of occurrences
	 * @throws IndexOutOfBoundsException
	 */
	public static int getOccurrences(final int firstSelectedDay, final String selectedDays,
	                                 final long durationInDays, final int periodicity) {
		int count = 0;
		int k = firstSelectedDay;
		int i = 0;

		while (i <= durationInDays) {
			if (k >= 7) {
				k = k % 7;
			}
			if (selectedDays.charAt(k) == '1') {
				count++;
			}
			k++;
			if (k == 1) {
				// On monday, go to next week
				i += (periodicity - 1) * 7;
			}
			i++;
		}

		return count;
	}

	/**
	 * @param occurrences   : number of occurrences
	 * @param periodicity   : slots are repeated every "periodicity" week
	 * @param firstSlotDate : Unix timestamp of the first slot's date (start or end date)
	 * @param firstSlotDay  : index of the first slot's day
	 * @param selectedDays  : bit string, used like an array of boolean, representing selected days. Char 0 is sunday, char 1 monday...
	 * @return Unix timestamp of the last slot's date (start or end date)
	 * @throws IllegalArgumentException, IndexOutOfBoundsException
	 */
	public static long getLastSlotDate(final int occurrences, final int periodicity,
	                                   final long firstSlotDate, final int firstSlotDay, final String selectedDays) {

		long lastSlotDate = firstSlotDate;
		if (occurrences > 1) {
			int selectedDay = firstSlotDay;
			int intervalFromFirstDay = 0;

			for (int i = 1; i <= (occurrences - 1); i++) {
				int interval = getNextInterval(selectedDay, selectedDays, periodicity);
				intervalFromFirstDay += interval;
				selectedDay = (selectedDay + interval) % 7;
			}
			lastSlotDate += TimeUnit.SECONDS.convert(intervalFromFirstDay, TimeUnit.DAYS);
		}

		return lastSlotDate;
	}

	/**
	 * @return Current unix timestamp in seconds
	 */
	public static long getCurrentTimestamp() {
		return TimeUnit.SECONDS.convert(Calendar.getInstance().getTimeInMillis(), TimeUnit.MILLISECONDS);
	}

	/**
	 * @return Unix timestamp of tomorrow midnight in seconds
	 */
	public static long getTomorrowTimestamp() {
		// Get tomorrow by adding one day to current time
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DAY_OF_MONTH, 1);

		// Set time to midnight
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);

		return TimeUnit.SECONDS.convert(cal.getTimeInMillis(), TimeUnit.MILLISECONDS);
	}

	/**
	 * @param unixTimestamp in seconds
	 * @return 0 for sunday, 1 for monday, etc
	 */
	public static int getDayFromTimestamp(final long unixTimestamp) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(TimeUnit.MILLISECONDS.convert(unixTimestamp, TimeUnit.SECONDS));
		// "- 1", so that sunday is 0, monday is 1, etc
		int day = cal.get(Calendar.DAY_OF_WEEK) - 1;

		return day;
	}

	public static boolean haveSameTime(final long thisTimestamp, final long thatTimestamp) {
		TimeZone gmt = TimeZone.getTimeZone("GMT");

		Calendar thisCal = Calendar.getInstance(gmt);
		thisCal.setTimeInMillis(
				TimeUnit.MILLISECONDS.convert(thisTimestamp, TimeUnit.SECONDS));

		Calendar thatCal = Calendar.getInstance(gmt);
		thatCal.setTimeInMillis(
				TimeUnit.MILLISECONDS.convert(thatTimestamp, TimeUnit.SECONDS));

		return (thisCal.get(Calendar.HOUR_OF_DAY) == thatCal.get(Calendar.HOUR_OF_DAY)
				&& thisCal.get(Calendar.MINUTE) == thatCal.get(Calendar.MINUTE)
				&& thisCal.get(Calendar.SECOND) == thatCal.get(Calendar.SECOND));
	}

	/**
	 * @return Return scope (i.e. the list of school_ids) of a local administrator
	 */
	public static List<String> getLocalAdminScope(final UserInfos user) {
		Map<String, UserInfos.Function> functions = user.getFunctions();
		if (functions != null && functions.containsKey(DefaultFunctions.ADMIN_LOCAL)) {
			Function adminLocal = functions.get(DefaultFunctions.ADMIN_LOCAL);
			if (adminLocal != null) {
				return adminLocal.getScope();
			}
		}

		return new ArrayList<String>();
	}

	public static List<String> getUserIdAndGroupIds(UserInfos user) {
		final List<String> groupsAndUserIds = new ArrayList<>();
		groupsAndUserIds.add(user.getUserId());
		if (user.getGroupsIds() != null) {
			groupsAndUserIds.addAll(user.getGroupsIds());
		}
		return groupsAndUserIds;
	}

	public static boolean isDelayLessThanMin(JsonObject resource, long startDate, long now) {
		long minDelay = resource.getLong("min_delay", -1);
		long delay = startDate - now;

		return (minDelay > -1 && minDelay > delay);
	}

	public static boolean isDelayGreaterThanMax(JsonObject resource, long endDate, long now) {
		long maxDelay = resource.getLong("max_delay", -1);
		if (maxDelay == -1) {
			return false;
		}

		// Authorize users to book a resource N days in advance, without taking hour/minute/seconds into account
		maxDelay = (getTomorrowTimestamp() - now) + maxDelay;
		long delay = endDate - now;

		return (delay > maxDelay);
	}

	/**
	 * Transforms an SQL formatted date to a java date
	 *
	 * @param strDate formatted string to be transformed from SQL type
	 * @return parsed java date
	 */
	public static Date parseDateFromDB(String strDate) throws ParseException {
		String format = DATE_FORMAT;
		// the format is adapted to SQL. So we have to parse and replace some fields.
		format = format.replace("MI", "mm");
		format = format.replace("HH24", "HH");
		format = format.replace("DD", "dd");
		format = format.replace("YY", "yy");
		SimpleDateFormat formatter = new SimpleDateFormat(format);
		return formatter.parse(strDate);
	}

	/**
	 * Transforms an SQL formatted timestamp to a java date
	 *
	 * @param strDate formatted string to be transformed from SQL type
	 * @return parsed java date
	 */
	public static Date parseTimestampFromDB(String strDate) throws ParseException {
		String format = TIMESTAMP_FORMAT;

		SimpleDateFormat formatter = new SimpleDateFormat(format);
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		return formatter.parse(strDate);
	}


	/**
	 * Owner or managers of a resourceType, as well as local administrators of a resourceType's schoolId,
	 * do no need to respect constraints on resources' delays
	 */
	public static boolean canBypassDelaysConstraints(String owner, String schoolId, UserInfos user, JsonArray managers) {
		if (user.getUserId().equals(owner)) {
			return true;
		}

		List<String> scope = getLocalAdminScope(user);
		if (scope != null && !scope.isEmpty() && scope.contains(schoolId)) {
			return true;
		}

		if (managers != null && managers.size() > 0) {
			// Create a list containing userId and groupIds of current user
			List<String> userAndGroupIds = new ArrayList<>();
			userAndGroupIds.add(user.getUserId());
			userAndGroupIds.addAll(user.getGroupsIds());

			// Return true if managers and userAndGroupIds have at least one common element
			if (!Collections.disjoint(userAndGroupIds, managers.toList())) {
				return true;
			}
		}

		return false;
	}
}
