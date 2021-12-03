import {_, angular, ng, idiom as lang} from 'entcore';
import {ROOTS} from "../../core/const/roots.const";
import {ExportBooking} from "../../models/export-booking.model";
import moment from "../../moment";
import {DateUtils} from "../../utilities/date.util";
import {FORMAT} from "../../core/const/date-format.const";

declare let saveAs: any;
declare let BlobBuilder: any;

interface IViewModel {
    $onInit(): any;
    $onDestroy(): any;
    initExportBooking(): void;
    toggleLightbox(state: boolean): void;
    checkMinExportDate(): void;
    checkMaxExportDate(): void;
    closeExport(): void;
    saveExport(): void;
    checkResourcesExport(resourcesToTake: any): string;
    checkViewExport(view: any): string;
    exportForm(): void;

    exportComponent: ExportBooking;
    minExportDate: Date;
    maxExportDate: Date;
    displayLightbox: boolean;

    /* props */

    display: any;
    resourceTypes: Array<any>;

}

export const exportBooking = ng.directive('exportBooking', function () {
    return {
        scope: {
            resourceTypes: '=',
            display: '='
        },
        restrict: 'E',
        templateUrl: `${ROOTS.directive}/export-booking/export-booking-form.html`,
        controllerAs: 'vm',
        bindToController: true,
        replace: false,
        controller: function () {
            const vm: IViewModel = <IViewModel>this;

            vm.$onInit = async () => {
                vm.initExportBooking();
                vm.displayLightbox = false;
            }

            vm.initExportBooking = (): void => {
                vm.exportComponent = new ExportBooking();
                vm.exportComponent.display = {
                    state: 0,
                    STATE_FORMAT: 0,
                    STATE_RESOURCES: 1,
                    STATE_DATE: 2,
                    STATE_VIEW: 3
                };
                vm.minExportDate = moment().week(moment().week() - 12).day(1).toDate();
                vm.maxExportDate = moment().week(moment().week() + 12).day(7).toDate();
            };
        },

        link: function ($scope) {
            const vm: IViewModel = $scope.vm;

            vm.toggleLightbox = (state: boolean): void => {
                if (state) {
                    vm.initExportBooking();
                }
                vm.displayLightbox = state;
            };

            vm.checkMinExportDate = (): void => {
                if (vm.exportComponent.startDate < vm.minExportDate) {
                    vm.exportComponent.startDate = vm.minExportDate;
                }
                vm.maxExportDate = moment(vm.exportComponent.startDate).week(moment(vm.exportComponent.startDate).week() + 12).day(7).toDate();
            }

            vm.checkMaxExportDate = (): void => {
                if (vm.exportComponent.endDate > vm.maxExportDate) {
                    vm.exportComponent.endDate = vm.maxExportDate;
                }
                vm.minExportDate = moment(vm.exportComponent.endDate).week(moment(vm.exportComponent.endDate).week() - 12).day(1).toDate();
            }

            vm.closeExport = (): void => {
                vm.toggleLightbox(false);
            }

            vm.saveExport = (): void => {
                
                vm.exportComponent.startDate = DateUtils.formatMoment(vm.exportComponent.startDate);
                vm.exportComponent.startDate = vm.exportComponent.startDate.format(FORMAT.formattedDate);
                
                vm.exportComponent.endDate = DateUtils.formatMoment(vm.exportComponent.endDate);
                vm.exportComponent.endDate = vm.exportComponent.endDate.format(FORMAT.formattedDate);
                
                if (vm.exportComponent.format === "ICal") {
                    vm.exportComponent.exportView = "NA";
                }
                if (vm.exportComponent.resourcesToTake === "selected") {
                    vm.resourceTypes.forEach(resourceType => {
                        resourceType.resources.forEach(resource =>  {
                            if (resource.selected) {
                                vm.exportComponent.resources.push(resource.id);
                            }
                        });
                    });
                } else {
                    vm.resourceTypes.forEach(resourceType => {
                        resourceType.resources.forEach(resource => {
                            vm.exportComponent.resources.push(resource.id);
                        });
                    });
                }

                if (vm.exportComponent.format === 'ICal') {
                    vm.exportComponent.send().then(data => {
                        let blob;
                        if (navigator.userAgent.indexOf('MSIE 10') === -1) { // chrome or firefox
                            blob = new Blob([data], {type: 'application/pdf;charset=utf-8'});
                        } else { // ie
                            let bb = new BlobBuilder();
                            bb.append(data);
                            blob = bb.getBlob('text/x-vCalendar;charset=' + document.characterSet);
                        }
                        saveAs(blob, moment().format(FORMAT.formattedDate) + '_export-reservations.ics');
                    }).catch(err => {

                    });
                } else {
                    let xsrfCookie;
                    if (document.cookie) {
                        var cookies = _.map(document.cookie.split(';'), function (c) {
                            return {
                                name: c.split('=')[0].trim(),
                                val: c.split('=')[1].trim()
                            };
                        });
                        xsrfCookie = _.findWhere(cookies, {name: 'XSRF-TOKEN'});
                    }
                    var xhr = new XMLHttpRequest();
                    xhr.open('POST', '/rbs/bookings/export', true);
                    if (xsrfCookie) {
                        xhr.setRequestHeader('X-XSRF-TOKEN', xsrfCookie.val);
                    }
                    xhr.responseType = "arraybuffer";
                    xhr.setRequestHeader("Content-type", "application/pdf");

                    xhr.addEventListener("load", function (evt: any) {
                        var data = evt.target.response;
                        if (this.status === 200) {
                            var blob = new Blob([data], {type: "application/pdf;charset=utf-8"});
                            saveAs(blob, moment().format(FORMAT.formattedDate) + '_export-reservations.pdf');
                        }
                    }, false);

                    xhr.send(angular.toJson(vm.exportComponent.toJSON()));
                }
                vm.closeExport();
            }

            vm.checkResourcesExport = (resourcesToTake: any): string => {
                if (resourcesToTake === "selected") {
                    return lang.translate('rbs.export.resource.selected.summary');
                } else {
                    return lang.translate('rbs.export.resource.all.summary');
                }
            };

            vm.checkViewExport = (view: any): string => {
                if (view === "DAY") {
                    return lang.translate('rbs.export.view.day')
                } else if (view === "WEEK") {
                    return lang.translate('rbs.export.view.week')
                } else {
                    return lang.translate('rbs.export.view.list')
                }
            };

            vm.$onDestroy = async () => {
            };
        }
    };
});