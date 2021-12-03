import {angular, moment} from "entcore";
import {RBS_CALENDAR_EVENTER} from "../core/enum/rbs-calendar-eventer.enum";

export class CalendarUtil {
    /**
     * Change position of calendar's button filter to the head (see main-calendar.html) directive <filters>
     *
     * @param $timeout  $timeout controller
     * @param $scope    $scope's controller
     * @param exit
     */
    static placingButton($timeout, $scope, exit): void {
        var done = false;
        let $calendarOptionButtonGroup: JQuery = $('.calendarOptionButtonGroup');
        /* trigger tooltip to show up */
        let $scheduleItems: JQuery = $('.schedule-items');
        $scheduleItems.mousemove(() => {
            $timeout(() => $scope.$apply(), 500)
        });
        $timeout(function () {
            if ($calendarOptionButtonGroup.length > 0 && $('.filters-icons > ul').length > 0) {
                $calendarOptionButtonGroup.children().appendTo('.filters-icons > ul');
                done = true;
            }
        }, 50).then(function () {
            if (!done && exit < 50) CalendarUtil.placingButton($timeout, $scope, ++exit)
        });
    }

    static fixViewFromNotification(): void {
        let list = document.getElementsByTagName("lightbox");
        for (var i = 0; i < list.length; i++) {
            if (list[i].getAttribute("show") === 'display.showPanel') {
                list[i].getElementsByTagName("section")[0].setAttribute('style','display:block;');
            }
        }
    }
}