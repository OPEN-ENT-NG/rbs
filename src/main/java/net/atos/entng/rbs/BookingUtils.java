package net.atos.entng.rbs;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class BookingUtils {

	/**
	 *
	 * @param lastSelectedDay : index of last selected day
	 * @param selectedDays : bit string, used like an array of boolean, representing selected days. Char 0 is sunday, char 1 monday...
	 * @param periodicity : slots are repeated every "periodicity" week
	 * @return Number of days until next slot
	 *
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
			}
			else if (k >= 7) {
				k = k % 7;
			}
			count++;
		} while (selectedDays.charAt(k) != '1');

		return count;
	}

	/**
	 *
	 * @param firstSelectedDay : index of the first slot's day
	 * @param selectedDays : bit string, used like an array of boolean, representing selected days. Char 0 is sunday, char 1 monday...
	 * @param durationInDays : difference in days between the first slot's end date and the end date of the periodic booking
	 * @param periodicity : slots are repeated every "periodicity" week
	 * @return Number of occurrences
	 *
	 * @throws IndexOutOfBoundsException
	 */
	public static int getOccurrences(final int firstSelectedDay, final String selectedDays,
			final long durationInDays, final int periodicity) {
		int count = 0;
		int k = firstSelectedDay;
		int i = 0;

		while(i <= durationInDays) {
			if(k >= 7) {
				k = k % 7;
			}
			if(selectedDays.charAt(k) == '1') {
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
	 *
	 * @param occurrences : number of occurrences
	 * @param periodicity : slots are repeated every "periodicity" week
	 * @param firstSlotDate : Unix timestamp of the first slot's date (start or end date)
	 * @param firstSlotDay : index of the first slot's day
	 * @param selectedDays : bit string, used like an array of boolean, representing selected days. Char 0 is sunday, char 1 monday...
	 * @return Unix timestamp of the last slot's date (start or end date)
	 *
	 * @throws IllegalArgumentException, IndexOutOfBoundsException
	 */
	public static long getLastSlotDate(final int occurrences, final int periodicity,
			final long firstSlotDate, final int firstSlotDay, final String selectedDays) {

		long lastSlotDate = firstSlotDate;
		if(occurrences > 1) {
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
	  *
	  * @param unixTimestamp in seconds
	  * @return 0 for sunday, 1 for monday, etc
	  */
	public static int getDayFromTimestamp(final long unixTimestamp) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(
				TimeUnit.MILLISECONDS.convert(unixTimestamp, TimeUnit.SECONDS));
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

}
