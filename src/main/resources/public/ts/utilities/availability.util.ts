import {notify} from "entcore";
import moment from "../moment";
import {Moment} from "moment";
import {BookingUtil} from "./booking.util";

export class AvailabilityUtil {
    /**
     * Method that check if an availability is valid
     *
     * @param availability      availability
     * @param today             current day
     * @param currentErrors     (optional) array that should be containing error to push in any controller
     * @return boolean          true if has error (supposedly is past)
     */
    static isAvailabilityInvalid = (availability: any, today: Moment, currentErrors?: Array<any>) : boolean => {
        let areDateTimesInvalid = AvailabilityUtil.areAvailabilityMomentsInvalid(availability, today, currentErrors);
        let areDaysInvalid = AvailabilityUtil.areAvailabilityDaysInvalid(availability, currentErrors);
        let isAvailabilityInvalid = areDateTimesInvalid || areDaysInvalid;
        if (isAvailabilityInvalid) notify.error(currentErrors[0].error);
        return isAvailabilityInvalid;
    };

    /**
     * Method that check if an availability days are ok
     *
     * @param availability      availability
     * @param currentErrors     (optional) array that should be containing error to push in any controller
     * @return boolean          true if has error (supposedly is past)
     */
    static areAvailabilityDaysInvalid = (availability: any, currentErrors?: Array<any>) : boolean => {
        let hasErrors = false;
        if (!availability.days.includes(true) &&
            (!currentErrors || !currentErrors.find(err => err.error === 'rbs.availability.invalid.days.empty'))) {
            currentErrors.push({error: 'rbs.availability.invalid.days.empty'});
            hasErrors = true;
        }

        return hasErrors;
    };

    /**
     * Method that check if an availability date and times are ok
     *
     * @param availability      availability
     * @param today             current day
     * @param currentErrors     (optional) array that should be containing error to push in any controller
     * @return boolean          true if has error (supposedly is past)
     */
    static areAvailabilityMomentsInvalid = (availability: any, today: Moment, currentErrors?: Array<any>) : boolean => {
        let hasErrors = false;
        // Check is past
        if (availability.start_date.isBefore(today, 'days') &&
            (!currentErrors || !currentErrors.find(err => err.error === 'rbs.availability.invalid.date.past'))) {
            currentErrors.push({error: 'rbs.availability.invalid.date.past'});
            hasErrors = true;
        }
        // Check is dates equal
        if (availability.start_date.isSame(availability.end_date, 'days') &&
            (!currentErrors || !currentErrors.find(err => err.error === 'rbs.availability.invalid.date.equals'))) {
            currentErrors.push({error: 'rbs.availability.invalid.date.equals'});
            hasErrors = true;
        }
        // Check is dates switched
        if (availability.start_date.isAfter(availability.end_date) &&
            (!currentErrors || !currentErrors.find(err => err.error === 'rbs.availability.invalid.date.switched'))) {
            currentErrors.push({error: 'rbs.availability.invalid.date.switched'});
            hasErrors = true;
        }
        // Check is times equal
        if (availability.start_time.isSame(availability.end_time, 'minutes') &&
            (!currentErrors || !currentErrors.find(err => err.error === 'rbs.availability.invalid.time.equals'))) {
            currentErrors.push({error: 'rbs.availability.invalid.time.equals'});
            hasErrors = true;
        }
        // Check is times switched
        if (availability.start_time.isAfter(availability.end_time) &&
            (!currentErrors || !currentErrors.find(err => err.error === 'rbs.availability.invalid.time.switched'))) {
            currentErrors.push({error: 'rbs.availability.invalid.time.switched'});
            hasErrors = true;
        }

        return hasErrors;
    };

    /**
     * Calculate quantity available for a timeslot given by a booking
     *
     * @param booking                   booking giving timeslot to check
     * @param resource                  resource of the booking
     * @param availabilityException     (un)availability not to count for calculus
     * @param bookingException          booking not to count for calculus
     * @return number                   quantity still available for this timeslot
     */
    static getTimeslotQuantityAvailable = (booking: any, resource: any, availabilityException?: any, bookingException?: any) : number => {
        let resourceQuantityDispo = AvailabilityUtil.getResourceQuantityByTimeslot(booking, resource, availabilityException);
        let bookingsQuantityUsed = AvailabilityUtil.getBookingsQuantityUsedOnTimeslot(booking, resource, bookingException);
        return resourceQuantityDispo - bookingsQuantityUsed;
    };

    /**
     * Calculate resource quantity available for a specifique timeslot gave by a booking
     *
     * @param booking       booking giving timeslot to check
     * @param resource      resource of the booking
     * @param exception     (un)availability not to count for calculus
     * @return number       resource quantity available on this timeslot
     */
    static getResourceQuantityByTimeslot = (booking: any, resource: any, exception?: any) : number => {
        let resourceQuantityDispo = resource.is_available ? resource.quantity : 0;
        let exceptionId = (exception && exception.id) ? exception.id : -1;

        if (resource.is_available) {
            for (let unavailability of resource.unavailabilities.all) {
                if (unavailability.id != exceptionId && AvailabilityUtil.isBookingOverlappingAvailability(booking, unavailability)) {
                    resourceQuantityDispo -= unavailability.quantity;
                }
            }
        }
        else {
            for (let availability of resource.availabilities.all) {
                if (availability.id != exceptionId && AvailabilityUtil.isBookingCoveredByAvailability(booking, availability)) {
                    resourceQuantityDispo += Math.min(resource.quantity, availability.quantity);
                }
            }
        }

        return resourceQuantityDispo;
    };

    /**
     * Calculate quantity used by the bookings overlapping our given timeslot
     *
     * @param booking       booking giving timeslot to check
     * @param resource      resource of the booking
     * @param exception     booking not to count for calculus
     * @return number       quantity used by the bookings using this timeslot
     */
    static getBookingsQuantityUsedOnTimeslot = (booking: any, resource: any, exception?: any) : number => {
        let bookings = resource.bookings.all;
        let bookingsQuantityUsed = 0;
        let exceptionId = (exception && exception.id) ? exception.id : -1;

        for (let b of bookings) {
            if (b.id != exceptionId && b.status != 3 && !b.is_periodic && BookingUtil.isBookingsOverlapping(booking, b)) {
                bookingsQuantityUsed += b.quantity;
            }
        }

        return bookingsQuantityUsed;
    };

    /**
     * Check if a booking overlaps an (un)availability
     *
     * @param booking
     * @param availability
     * @return boolean
     */
    static isBookingOverlappingAvailability = (booking: any, availability: any) : boolean => {
        return !booking.is_periodic &&
            BookingUtil.isNotPast(booking) &&
            AvailabilityUtil.isCheckedDay(booking, availability) &&
            moment(booking.startMoment).format("YYYY/MM/DD") <= moment(availability.end_date).format("YYYY/MM/DD") &&
            moment(booking.startMoment).format("HH:mm") < moment(availability.end_time).format("HH:mm") &&
            moment(booking.endMoment).format("YYYY/MM/DD") >= moment(availability.start_date).format("YYYY/MM/DD") &&
            moment(booking.endMoment).format("HH:mm") > moment(availability.start_time).format("HH:mm")
    };

    /**
     * Check if a booking is totally covered by an (un)availability
     *
     * @param booking
     * @param availability
     * @return boolean
     */
    static isBookingCoveredByAvailability = (booking: any, availability: any) : boolean => {
        return !booking.is_periodic &&
            BookingUtil.isNotPast(booking) &&
            AvailabilityUtil.isCheckedDay(booking, availability) &&
            moment(booking.startMoment).format("YYYY/MM/DD") >= moment(availability.start_date).format("YYYY/MM/DD") &&
            moment(booking.startMoment).format("HH:mm") >= moment(availability.start_time).format("HH:mm") &&
            moment(booking.endMoment).format("YYYY/MM/DD") <= moment(availability.end_date).format("YYYY/MM/DD") &&
            moment(booking.endMoment).format("HH:mm") <= moment(availability.end_time).format("HH:mm")
    };

    /**
     * Check if a booking is on an (un)availability's checked day
     *
     * @param booking
     * @param availability
     * @return boolean
     */
    static isCheckedDay = (booking: any, availability: any) : boolean => {
        for (let i = 0; i < availability.days.length; i++) {
            let day = availability.days[i];
            for (let d = booking.startMoment.day(); d <= booking.endMoment.day(); d++) {
                if (day && d === i) {
                    return true;
                }
            }
        }
        return false;
    };
}