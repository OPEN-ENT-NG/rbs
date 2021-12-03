export class ArrayUtil {

    static sort_by = function (field, reverse, primer): any {
        var key = primer
            ? function (x) {
                return primer(x[field]);
            }
            : function (x) {
                return x[field];
            };

        reverse = !reverse ? 1 : -1;

        return function (a, b) {
            return (a = key(a)), (b = key(b)), reverse * ((a > b) as any - ((b > a) as any));
        };
    };

}