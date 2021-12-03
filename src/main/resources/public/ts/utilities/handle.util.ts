export class HandleUtil {

    /**
     * Customize your error type you wish to display by specifying the key word found in your sql/other error
     * (e.g: fetching an error with "valid_dates" key words handled by your psql will turn into bad request invalid dates)
     *
     * @param e Error Type ({context: string, error: string, object: any, status: number})
     */
    public static handleErrorType = (e: {context: string, error: string, object: any, status: number}): void => {
        const error: string = e.error;
        const findTerm = (term: string): string => {
            if (error.includes(term)) {
                return error;
            }
        };
        switch (error) {
            case findTerm('valid_dates'): {
                e.error = "rbs.booking.bad.request.invalid.dates";
            }
        }
    };
}