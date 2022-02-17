import {moment} from "entcore";
import {Moment} from "moment";

export class DateUtils {
    /**
     * Format date based on given format using moment
     * @param date date to format
     * @param format format
     */
    static format(date: any, format: string) {
        return moment(date).format(format);
    }

    /**
     * build moment date type
     *
     * @param date Date (containing year, month and day data in Data)
     * @param time format (Moment that must contain hour and minute data)
     * @return Moment
     */
    static formatMoment(date: Date, time?: Moment): Moment {
        let dateToAppend: Array<any> = [
            date.getFullYear(),
            date.getMonth(),
            date.getDate(),
        ];

        if (time) {
            dateToAppend.push(time.hour());
            dateToAppend.push(time.minute());
        }
        return moment(dateToAppend);
    }

    /**
     * Check if the value passed in parameter is past or not
     *
     * @param date      moment value to check if it is not past
     * @return boolean
     */
    static isNotPast(date: Moment): boolean {
        return(moment(date).isAfter(moment()));
    };


    static formatTimeIfString(date: any): any {
        if (typeof date === 'string') {
            const time = date.split(':');
            return moment().set('hour', time[0]).set('minute', time[1]);
        }
        return(date);
    };

    static formatMomentIfDate(date: any): any {
        if (date instanceof Date) {
            return DateUtils.formatMoment(date);
        }
        return(date);
    };

    static displayTime = (date) => {
        return DateUtils.format(date,'HH[h]mm');
    };

    static displayDate = (date) => {
        return DateUtils.format(date,'DD/MM/YYYY');
    };
}