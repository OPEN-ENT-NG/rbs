import {_, Behaviours} from "entcore";

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
}