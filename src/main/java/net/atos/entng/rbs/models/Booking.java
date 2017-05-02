package net.atos.entng.rbs.models;

import java.util.Optional;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class Booking {
	private final String bookingId;
	private final JsonObject json;
	private Resource resource;
	private Slots slots = new Slots();
	private String selectedDaysBitString;

	public Booking(JsonObject json, Resource resource) {
		this(json, resource, null);
	}

	public Booking(JsonObject json, Resource resource, final String id) {
		super();
		this.json = json;
		this.bookingId = id;
		this.resource = resource;
	}

	public Booking(JsonObject json, final String id, Slots slots) {
		super();
		this.json = json;
		this.bookingId = id;
		this.slots = slots;
	}

	public void setResource(Resource resource) {
		this.resource = resource;
	}
	public void setSlots(Slots slots) {
		this.slots = slots;
	}
	public String getBookingId() {
		return bookingId;
	}

	public JsonObject getJson() {
		return json;
	}

	public String getIana() {
		return json.getString("iana");
	}

	public Slots getSlots() { return  this.slots.size() == 0 ? new Slots(json.getJsonArray("slots", null)) : this.slots ; }

	public Object getRawStartDate() {
		return this.json.getValue("start_date");
	}

	public Object getRawEndDate() {
		return this.json.getValue("end_date");
	}




	public int getPeriodicity() {
		return json.getInteger("periodicity");
	}

	public long getPeriodicEndDateAsUTCSeconds() {
		return json.getLong("periodic_end_date", 0l);
	}

	public void setPeriodicEndDateAsUTCSeconds(long timestamp) {
		json.put("periodic_end_date", timestamp);
	}

	public String getBookingReason() {
		return json.getString("booking_reason");
	}

	public Optional<JsonArray> getDays() {
		return Optional.ofNullable(json.getJsonArray("days", null));
	}

	public int getOccurrences(int defaut) {
		return json.getInteger("occurrences", defaut);
	}

	public void setOccurrences(int occurences) {
		json.put("occurrences", occurences);
	}

	public Optional<Long> getMaxDelayAsSeconds() {
		return resource.getMaxDelayAsSeconds();
	}

	public Optional<Long> getMinDelayAsSeconds() {
		return resource.getMinDelayAsSeconds();
	}

	public Long minDelayAsDay() {
		return BookingDateUtils.secondToDays(getMinDelayAsSeconds().orElse(-1l));
	}

	public Long maxDelayAsDay() {
		return BookingDateUtils.secondToDays(getMaxDelayAsSeconds().orElse(-1l));
	}

	public String getSelectedDaysBitString() {
		if (selectedDaysBitString == null) {
			computeSelectedDaysAsBitString();
		}
		return selectedDaysBitString;
	}
	public int dayOfWeekForPeriodicEndDate() {
		final long firstSlotEndDate = getPeriodicEndDateAsUTCSeconds();
		return BookingDateUtils.dayOfWeekForTimestampSecondsAndIana(firstSlotEndDate, getIana());
	}

	public boolean hasPeriodicEndDate() {
		return getPeriodicEndDateAsUTCSeconds() > 0l;
	}

	public boolean hasMinDelay() {
		return this.getMinDelayAsSeconds().isPresent();
	}

	public boolean hasMaxDelay() {
		return this.getMaxDelayAsSeconds().isPresent();
	}

	public boolean slotsNotRespectingMinDelay() {
		Optional<Long> minDelay = getMinDelayAsSeconds();
		return this.getSlots().areNotRespectingMinDelay(minDelay);
	}
    public boolean slotsNotRespectingMaxDelay() {
        Long maxDelay = getMaxDelayAsSeconds().orElse(-1l);
        if (maxDelay == -1) {
            return false;
        }
        return this.getSlots().areNotRespectingMaxDelay(this,maxDelay);
    }

	public boolean isNotPeriodic() {
		final long endDate = getPeriodicEndDateAsUTCSeconds();
		final int occurrences = getOccurrences(0);
		return endDate == 0L && occurrences == 0;
	}

	public boolean hasPeriodicEndAsLastDay() {
		final int endDateDay = dayOfWeekForPeriodicEndDate();
		JsonArray selectedDaysArray = getDays().get();
		Object endDateDayIsSelected = selectedDaysArray.getList().get(endDateDay);
		return (Boolean) endDateDayIsSelected;
	}

	public boolean hasNotSelectedDays() {
		final Optional<JsonArray> selectedDaysArrayOpt = getDays();
		return (!selectedDaysArrayOpt.isPresent() || selectedDaysArrayOpt.get().size() != 7);
	}

	public boolean hasNotSelectedStartDayOfWeek() {
        boolean oneFound = true;
        for(Slot slot : this.getSlots()){
			final int startDay = slot.dayOfWeekForStartDate();
            JsonArray selectedDaysArray = getDays().orElse(null);
            if (selectedDaysArray == null) {
                return true;
            }
            Object firstSlotDayIsSelected = selectedDaysArray.getList().get(startDay);
            if(!(boolean) firstSlotDayIsSelected){ oneFound = false;}
        }
        return !oneFound;
	}



	public void computeSelectedDaysAsBitString() {
		JsonArray selectedDaysArray = getDays().get();
		StringBuilder selectedDays = new StringBuilder();
		for (Object day : selectedDaysArray) {
			int isSelectedDay = ((Boolean) day) ? 1 : 0;
			selectedDays.append(isSelectedDay);
		}
		selectedDaysBitString = selectedDays.toString();
	}

	public long daysBetweenFirstSlotEndAndPeriodicEndDate(Slot slot) {
		long endDate = getPeriodicEndDateAsUTCSeconds();
		long firstSlotEnd = slot.getEndUTC();
		long duration = endDate - firstSlotEnd;
		return BookingDateUtils.secondToDays(duration);
	}

	/**
	 *
	 * firstSelectedDay : index of the first slot's day<br/>
	 * selectedDays : bit string, used like an array of boolean, representing
	 * selected days. Char 0 is sunday, char 1 monday...<br/>
	 * durationInDays : difference in days between the first slot's end date and the
	 * end date of the periodic booking<br/>
	 * periodicity : slots are repeated every "periodicity" week<br/>
	 * 
	 * @return Number of occurrences
	 *
	 * @throws IndexOutOfBoundsException
	 */
	public int countOccurrences(Slot slot) {
		final int periodicity = getPeriodicity();
		final long durationInDays = daysBetweenFirstSlotEndAndPeriodicEndDate(slot);
		final int firstSelectedDay = slot.dayOfWeekForStartDate();
		final String selectedDays = getSelectedDaysBitString();
		//
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
	 *
	 * occurrences : number of occurrences<br/>
	 * periodicity : slots are repeated every "periodicity" week<br/>
	 * firstSlotDate : Unix timestamp of the first slot's date (end date)<br/>
	 * firstSlotDay : index of the first slot's day<br/>
	 * selectedDays : bit string, used like an array of boolean, representing
	 * selected days. Char 0 is sunday, char 1 monday...<br/>
	 * 
	 * @return Unix timestamp of the last slot's date (start or end date)
	 *
	 * @throws IllegalArgumentException, IndexOutOfBoundsException
	 */
	public long getLastSlotDate(final int occurrences) {
		final Slot lastSlot = getSlots().getSlotWithLatestEndDate();
		final long firstSlotDate = lastSlot.getEndUTC();
		final int firstSlotDay =lastSlot.dayOfWeekForStartDate();
		//
		long lastSlotDate = firstSlotDate;
		if (occurrences > 1) {
			int selectedDay = firstSlotDay;
			int intervalFromFirstDay = 0;

			for (int i = 1; i <= (occurrences - 1); i++) {
				int interval = getNextIntervalAsDayOfWeek(selectedDay);
				intervalFromFirstDay += interval;
				selectedDay = (selectedDay + interval) % 7;
			}
			lastSlotDate += BookingDateUtils.daysToSecond(intervalFromFirstDay);
		}

		return lastSlotDate;
	}

	/**
	 *
	 * lastSelectedDay : index of last selected day<br/>
	 * selectedDays : bit string, used like an array of boolean, representing
	 * selected days. Char 0 is sunday, char 1 monday...<br/>
	 * periodicity : slots are repeated every "periodicity" week<br/>
	 * 
	 * @return Number of days until next slot
	 *
	 * @throws IllegalArgumentException, IndexOutOfBoundsException
	 */
	public int getNextIntervalAsDayOfWeek(final int lastSelectedDay) {
		final int periodicity = getPeriodicity();
		final String selectedDays = getSelectedDaysBitString();
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


	public long computeAndSetLastEndDateAsUTCSedonds() {
		if (hasPeriodicEndDate()) { // Case when end_date is supplied
			if (hasPeriodicEndAsLastDay()) {
				return getPeriodicEndDateAsUTCSeconds();
			} else {
				// If the endDateDay is not a selected day, compute the end date
				// of the last slot
				int nbOccurrences = countOccurrences(this.getSlots().getSlotWithLatestEndDate());
				final long lastSlotEndDate = getLastSlotDate(nbOccurrences);
				// Replace the end date with the last slot's end date
				setPeriodicEndDateAsUTCSeconds(lastSlotEndDate);
				// Put the computed value of occurrences
				setOccurrences(nbOccurrences);
				return lastSlotEndDate;
			}
		} else { // Case when occurrences is supplied
			return getLastSlotDate(getOccurrences(0));
		}
	}

}
