import {Availabilities} from "./Availability";
import {Bookings} from "./booking.model";
import {ResourceType} from "./resourcetype.model";

export class Resource {
    color: String;
    created: String;
    description: String; //HTML
    icon: any;
    id: number;
    is_available: boolean;
    max_delay: number;
    min_delay: number;
    modified: String;
    name: String;
    owner: String;
    periodic_booking: boolean;
    quantity: number;
    shared: Array<any>;
    type_id: number;
    validation: boolean;
    // visibility: null;

    availabilities: Availabilities;
    bookings: Bookings;
    myRights: any;
    notified: false
    selected: true
    type: ResourceType;
    unavailabilities: Availabilities;
}