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
        let hasErrors = false;
        if (
            booking.startDate.getFullYear() < today.year() ||
            (booking.startDate.getFullYear() == today.year() &&
                booking.startDate.getMonth() < today.month()) ||
            (booking.startDate.getFullYear() == today.year() &&
                booking.startDate.getMonth() == today.month() &&
                booking.startDate.getDate() < today.date()) ||
            (booking.startDate.getFullYear() == today.year() &&
                booking.startDate.getMonth() == today.month() &&
                booking.startDate.getDate() == today.date() &&
                booking.startTime.hour() < moment().hour())
        ) {
            if (currentErrors) {
                currentErrors.push({error: 'rbs.booking.invalid.datetimes.past'});
            }
            notify.error('rbs.booking.invalid.datetimes.past');
            hasErrors = true;
        }
        return hasErrors;
    };
}