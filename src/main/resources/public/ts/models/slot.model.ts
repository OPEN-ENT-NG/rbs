export interface ISlotResponse {
    id: String;
    endHour: String;
    name: String;
    startHour: String;
}

export class Slot {
    id: String;
    endHour: String;
    name: String;
    startHour: String;

    build(data: ISlotResponse): Slot {
        this.id = data.id;
        this.endHour = data.endHour;
        this.name = data.name;
        this.startHour = data.startHour;

        return this;
    }
}

export class SlotLit {
    slots: Array<Slot>;
}

export class SlotsForAPI {
    end_date: Date;
    iana: String;
    start_date: Date;
}

