export interface ISlotResponse {
    id: string;
    endHour: string;
    name: string;
    startHour: string;
}

export class Slot {
    id: string;
    endHour: string;
    name: string;
    startHour: string;

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
    iana: string;
    start_date: Date;
}

