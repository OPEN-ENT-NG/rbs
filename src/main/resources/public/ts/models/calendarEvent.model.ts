import {Booking, Bookings, IBookingResponse} from "./booking.model";

export interface ICalendarEventResponse {
    _id: string;
    bookings: Array<IBookingResponse|Bookings>;
    startMoment: Date;
    startTime: Date;
    endMoment: Date;
    endTime: Date;
    allday: boolean;
    hasBooking: boolean;
}

export class CalendarEvent {
    _id: string;
    bookings: Array<IBookingResponse|Bookings>;
    startMoment: Date;
    startTime: Date;
    endMoment: Date;
    endTime: Date;
    allday: boolean;
    hasBooking: boolean;

    build(data: ICalendarEventResponse): CalendarEvent {
        this._id = data._id;
        this.bookings = data.bookings;
        this.startMoment = data.startMoment;
        this.startTime = data.startTime;
        this.endMoment = data.endMoment;
        this.endTime = data.endTime;
        this.allday = data.allday;
        this.hasBooking = data.hasBooking;

        return this;
    }
}