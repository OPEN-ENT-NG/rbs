import {Resource} from "./resource.model";
import {Behaviours} from "entcore";

export class Structure {
    id: string;
    name: string;
}

export interface IResourceTypeResponse {
    id: string;
    color: string;
    created: string;
    extendcolor: boolean;
    modified: string;
    name: string;
    owner: string;
    school_id: string;
    shared: Array<any>;
    slotprofile: String;
    validation: boolean;
    visibility: boolean;
}

export class ResourceType {
    id: string;
    color: string;
    created: string;
    extendColor: boolean;
    modified: string;
    name: string;
    owner: string;
    schoolId: string;
    shared: Array<any>;
    slotProfile: String;
    validation: boolean;
    visibility: boolean;

    myRights: any;

    build(data: IResourceTypeResponse): ResourceType {
        this.id = data.id;
        this.color = data.color;
        this.created = data.created;
        this.extendColor = data.extendcolor;
        this.modified = data.modified;
        this.name = data.name;
        this.owner = data.owner;
        this.schoolId = data.school_id;
        this.shared = data.shared;
        this.slotProfile = data.slotprofile;
        this.validation = data.validation;
        this.visibility = data.visibility;

        Behaviours.applicationsBehaviours.rbs.resourceRights(this);

        return this;
    }
}

export class Data {
    color: string;
    created: string;
    extendcolor: boolean;
    id: number;
    modified: string;
    name: string;
    owner: string;
    school_id: string;
    shared: Array<any>;
    slotprofile: string;
    structure: Structure;
    validation: boolean;
    _id: number;

    booking_reason: string;
    end_date: string;
    isBookingInstance: boolean;
    is_periodic: boolean;
    owner_name: string;
    parent_booking_id: number;
    quantity: number;
    resource_id: number;
    start_date: string;
    status: number;
}

export class Moderator {
    id: string;
    login: string;
    type: string;
    username: string;
}
