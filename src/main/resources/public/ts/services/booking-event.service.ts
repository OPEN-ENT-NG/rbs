import {ng} from 'entcore'
import {Observable, Subject} from "rxjs";

export class BookingEventService {
    private subject = new Subject<string>();

    sendState(message: string): void {
        this.subject.next(message);
    }

    getState(): Observable<string> {
        return this.subject.asObservable();
    }

    unsubscribe(): void {
        this.subject.unsubscribe();
    }
}

export const bookingEventService = ng.service('BookingEventService', BookingEventService);
