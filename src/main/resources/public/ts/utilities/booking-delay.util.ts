import moment from "../moment";
import {Booking} from "../models/booking.model";

export class BookingDelayUtil {

    /**
     * Returns true if the minimum delay is compliant
     *
     * @param booking the studied booking
     */
    static checkMinDelay(booking: Booking): boolean {
        let delay = booking.startMoment.unix() - moment().unix();

        return (delay > booking.resource.minDelay);
    }

    /**
     * Returns true if the maximum delay is compliant
     *
     * @param booking the studied booking
     */
    static checkMaxDelay(booking: Booking): boolean {
        let delay = booking.startMoment.unix() - moment().unix();

        return (booking.resource.maxDelay > delay);
    }

    /**
     * Returns the number of days of the dela
     * @param delay
     */
    static displayDelayDays(delay: number): number {
        return Math.floor(delay / 86400);
    }
}