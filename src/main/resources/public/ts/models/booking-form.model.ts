export class Structure {
    id: string;
    name: string;
}

export class ResourceType {
    id: string;
    color: string;
    created: string;
    extendcolor: boolean;
    modified: string;
    name: string;
    owner: string;
    school_id: string;
    shared: Array<any>;
    validation: boolean;
    // slotprofile: ;
    // visibility: ;
}

export class Booking {
    id: string;
    structure: Structure;
    resourceType: ResourceType;
    resource: any;
}