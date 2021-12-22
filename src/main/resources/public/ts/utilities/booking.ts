import {_, Behaviours, notify} from "entcore";
import moment from "../moment";
import {Moment} from "moment";

export class BookingUtil {

    /**
     * Method that update/set `myRights` resource if data booking is not updated properly
     * @param booking Booking
     */
    static setRightsResourceBooking(booking: any): void {
        if (booking && booking.myRights && _.isEmpty(booking.myRights)) {
            Behaviours.applicationsBehaviours.rbs.resourceRights(booking);
        }
    }

    /**
     * Method that certified this item is a 'Booking' type data
     * @param bookings Array<Bookings> (Supposedly)
     */
    static setTagBookingInstance(bookings: any): void {
        bookings.forEach(b => b.isBookingInstance = true);
    }

    /**
     * Method that check if the current booking can be opened to create or edit (should not do in the past)
     *
     * @param booking           current booking
     * @param today             current day
     * @param currentErrors     (optional) array that should be containing error to push in any controller
     * @return boolean  (true if has error (supposedly is past), false if booking can be opened/edited
     */
    static checkEditedBookingMoments(booking: any, today: Moment, currentErrors?: Array<any>) : boolean {
        var hasErrors = false;
        if ((booking.startDate.getFullYear() < today.year() ||
            (booking.startDate.getFullYear() == today.year() &&
                booking.startDate.getMonth() < today.month()) ||
            (booking.startDate.getFullYear() == today.year() &&
                booking.startDate.getMonth() == today.month() &&
                booking.startDate.getDate() < today.date()) ||
            (booking.startDate.getFullYear() == today.year() &&
                booking.startDate.getMonth() == today.month() &&
                booking.startDate.getDate() == today.date() &&
                booking.startTime.hour() < moment().hour())) &&
            (!currentErrors || !currentErrors.find(err => err.error === 'rbs.booking.invalid.datetimes.past')))
        {
            currentErrors.push({error: 'rbs.booking.invalid.datetimes.past'});
            hasErrors = true;
        }

        if (booking.startDate.getFullYear() == booking.endDate.getFullYear() &&
            booking.startDate.getMonth() == booking.endDate.getMonth() &&
            booking.startDate.getDate() == booking.endDate.getDate() &&
            booking.endTime.hour() == booking.startTime.hour() &&
            booking.endTime.minute() == booking.startTime.minute() &&
            (!currentErrors || !currentErrors.find(err => err.error === 'rbs.booking.invalid.datetimes.equals')))
        {
            currentErrors.push({error: 'rbs.booking.invalid.datetimes.equals'});
            hasErrors = true;
        }

        if (booking.startDate.getTime() > booking.endDate.getTime() &&
            (!currentErrors || !currentErrors.find(err => err.error === 'rbs.booking.invalid.datetimes.switched')))
        {
            currentErrors.push({error: 'rbs.booking.invalid.datetimes.switched'});
            hasErrors = true;
        }

        return hasErrors;
    };

    /**
     * Check if two bookings overlaps
     *
     * @param date1    moment value to check if it overlaps date2
     * @param date2    moment value to check if it overlaps date1
     * @return boolean
     */
    static isBookingsOverlapping(date1: any, date2: any): boolean {
        return moment(date1.startMoment) < moment(date2.endMoment) && moment(date1.endMoment) > moment(date2.startMoment);
    };
}