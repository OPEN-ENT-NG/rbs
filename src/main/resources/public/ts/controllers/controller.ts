import {_, Behaviours, idiom as lang, ng, notify, template} from 'entcore';
import moment from '../moment';
import {isBookingSlot, RBS} from '../models/models';
import {BookingUtil} from "../utilities/booking.util";
import {DateUtils} from "../utilities/date.util";
import {CalendarUtil} from "../utilities/calendar.util";
import {ArrayUtil} from "../utilities/array.util";
import {BookingEventService, IBookingService} from "../services";
import {BOOKING_EVENTER} from "../core/enum/booking-eventer.enum";
import {safeApply} from "../utilities/safe-apply";
import {AvailabilityUtil} from "../utilities/availability.util";
import {availabilityService} from "../services/AvailabilityService";
import {Availabilities, Availability} from "../models/Availability";
import {Mix} from "entcore-toolkit";


const {Booking, Notification, Resource, ResourceType, SlotProfile} = RBS;

declare let model: any;
declare let window: any;

export const RbsController: any = ng.controller('RbsController', ['$scope', 'BookingService', 'route', '$timeout', 'BookingEventService',
    function ($scope, bookingService: IBookingService, route, $timeout, bookingEventService: BookingEventService) {
        route({
            viewBooking: function (param) {
                if (param.start) {
                    loadBooking(param.start, param.bookingId);
                } else {
                    new Booking().retrieve(
                        param.bookingId,
                        function (date) {
                            loadBooking(date, param.bookingId);
                        },
                        function (e) {
                            $scope.currentErrors.push(e);
                            $scope.safeApply();
                        }
                    );
                }
                $scope.showCalendar(false);
            }
        });

        $scope.safeApply = (): void => {
            return safeApply($scope);
        };

        this.$onInit = () => {
            model.calendar.eventer.on("calendar.create-item", function (timeSlot) {
                if (model.calendar.newItem && model.calendar.newItem.beginning.isBefore(moment().add(1, 'hours'))) {
                    notify.error('rbs.booking.invalid.datetimes.past');
                    return;
                }
                window.bookingState = BOOKING_EVENTER.CREATE_CALENDAR;
                template.open('lightbox', 'booking/edit-booking');
                $scope.display.showPanel = true;
            });

            bookingEventService.getState().subscribe((state: string) => {
                switch (state) {
                    case BOOKING_EVENTER.CLOSE:
                        $scope.closeBooking();
                        break;
                }
            });
        };

        this.$onDestroy = () => {
            bookingEventService.unsubscribe();
            delete window.bookingState;
        };

        const loadBooking = function (date, id) {
            model.bookings.startPagingDate = moment(date).startOf('isoweek');
            //Paging end date
            model.bookings.endPagingDate = moment(date).add(7, 'day').startOf('day');

            $scope.display.routed = true;
            $scope.resourceTypes.one('sync', function () {
                $scope.initResourcesRouted(id, function () {
                    // updateCalendarScheduleBooking(moment(date), true);
                });
            });
        };

        this.initialize = function () {
            model.makeModels(RBS);
            $scope.template = template;
            $scope.me = model.me;
            $scope.date = new Date();
            $scope.lang = lang;
            $scope.firstTime = true;
            model.calendar.name = 'rbs';
            moment.locale(window.navigator.language);
            model.calendar.firstDay = model.calendar.firstDay.lang(window.navigator.language);

            $scope.display = {
                list: false, // calendar by default
                create: false,
                showPanel: false,
            };

            $scope.sort = {
                predicate: 'start_date',
                reverse: false,
            };

            template.open('itemTooltip', 'calendar/tooltip-template');

            // Used to display types for moderators (moderators of a type can update a resource, but cannot create one)
            $scope.keepProcessableResourceTypes = function (type) {
                return type.myRights && type.myRights.process;
            };

            // Used to display types in which current user can create a resource
            $scope.keepManageableResourceTypes = function (type) {
                return type.myRights && type.myRights.manage;
            };

            $scope.status = {
                STATE_CREATED: model.STATE_CREATED,
                STATE_VALIDATED: model.STATE_VALIDATED,
                STATE_REFUSED: model.STATE_REFUSED,
                STATE_SUSPENDED: model.STATE_SUSPENDED,
                STATE_PARTIAL: model.STATE_PARTIAL,
            };
            $scope.today = moment().startOf('day');
            $scope.tomorrow = moment().add('day', 1).startOf('day');

            $scope.booking = new Booking();
            $scope.initBookingDates(moment(), moment());

            $scope.resourceTypes = model.resourceTypes;
            $scope.bookings = model.bookings;
            $scope.periods = model.periods;
            $scope.structures = model.structures;

            $scope.slotProfilesComponent = new SlotProfile();
            $scope.notificationsComponent = new Notification();
            $scope.editedAvailability = new Availability();

            $scope.structuresWithTypes = [];

            $scope.currentResourceType = undefined;
            $scope.selectedBooking = undefined;
            $scope.editedBooking = null;
            $scope.bookings.refuseReason = undefined;
            $scope.selectedStructure = undefined;
            $scope.processBookings = [];
            $scope.currentErrors = [];

            template.open('main', 'main-view');
            template.open('top-menu', 'top-menu');
            template.open('editBookingErrors', 'booking/edit-booking-errors');


            // fixme why to do that  Will auto-select first resourceType resources and no filter by default
            //model.recordedSelections.firstResourceType = true;
            model.recordedSelections.allResources = true;

            model.bookings.filters.dates = true;
            //Paging start date
            model.bookings.startPagingDate = moment().startOf('isoweek');
            //Paging end date
            model.bookings.endPagingDate = moment(model.bookings.startPagingDate)
                .add(1, 'week')
                .startOf('day');
            //fixme Why started with today date ....
            model.bookings.filters.startMoment = moment().startOf('day');
            //fixme Why two month ?
            model.bookings.filters.endMoment = moment().add('month', 2).startOf('day');
            model.bookings.filters.startDate = model.bookings.filters.startMoment.toDate();
            model.bookings.filters.endDate = model.bookings.filters.endMoment.toDate();

            model.calendar.on('date-change', function () {
                var start = moment(model.calendar.firstDay);
                var end = moment(model.calendar.firstDay)
                    .add(1, model.calendar.increment + 's')
                    .startOf('day');

                if (
                    model.bookings.startPagingDate.isSame(start, 'day') &&
                    model.bookings.endPagingDate.isSame(end, 'day')
                ) {
                    return;
                }

                model.bookings.startPagingDate = start;
                model.bookings.endPagingDate = end;

                updateCalendarSchedule(model.calendar.firstDay);
            });

            $scope.bookings.on('sync', () => {
                CalendarUtil.showTooltip($timeout, $scope);
                $scope.safeApply();
            });

            $scope.resourceTypes.on('sync', function () {
                // check create booking rights
                $scope.display.create = $scope.canCreateBooking();

                if ($scope.display.list === true) {
                    template.open('bookings', 'main-list');
                } else {
                    template.open('bookings', 'main-calendar');
                    CalendarUtil.placingButton($timeout, $scope, 0);
                }

                $scope.deleteTypesInStructures();
                if ($scope.isManage) {
                    $scope.initStructuresManage(false, $scope.currentResourceType);
                    $scope.isManage = undefined;
                } else {
                    $scope.initStructures();
                }
                // Do not restore if routed
                if ($scope.display.routed === true) {
                    return;
                }
                $scope.initResources();
                $scope.safeApply();
            });

            //when date picker of calendar directive is used
            $scope.$watch(
                function () {
                    return $('.hiddendatepickerform')[0]
                        ? $('.hiddendatepickerform')[0].nodeValue
                        : '';
                },
                function (newVal, oldVal) {
                    if (newVal !== oldVal) {
                        if (
                            !$scope.display.list &&
                            newVal &&
                            newVal !== '' &&
                            moment(model.bookings.startPagingDate).diff(
                                moment(newVal, 'DD/MM/YYYY').startOf('isoweek')
                            ) !== 0
                        ) {
                            model.bookings.startPagingDate = moment(
                                newVal,
                                'DD/MM/YYYY'
                            ).startOf('isoweek');
                            model.bookings.endPagingDate = moment(newVal, 'DD/MM/YYYY')
                                .startOf('isoweek')
                                .add(7, 'day')
                                .startOf('day');
                            $scope.bookings.sync();
                        }
                    }
                },
                true
            );
        };

        $scope.hasAnyBookingRight = function (booking) {
            return booking.resource.myRights.process || booking.resource.myRights.manage || booking.owner === model.me.userId;
        }

        // Initialization
        $scope.initResources = function () {
            var remanentBookingId =
                $scope.selectedBooking !== undefined
                    ? $scope.selectedBooking.id
                    : undefined;
            var remanentBooking = undefined;
            // Restore previous selections
            model.recordedSelections.restore(
                function (resourceType) {
                    $scope.currentResourceType = resourceType;
                },
                function (resource) {
                    resource.bookings.sync(function () {
                        if (remanentBookingId !== undefined) {
                            remanentBooking = resource.bookings.find(function (booking) {
                                return booking.id === remanentBookingId;
                            });
                            if (remanentBooking !== undefined) {
                                $scope.viewBooking(remanentBooking);
                            }
                        }
                    });
                }
            );
            model.recordedSelections.allResources = false;
        };

        $scope.getSharedResources = function (state) {
            $scope.sharedStructure = model.DETACHED_STRUCTURE;
            var structureState = state.find(function (struct) {
                return struct.id === $scope.sharedStructure.id
            });
            $scope.sharedStructure.expanded = structureState ? structureState.expanded : false;
            $scope.sharedStructure.selected = structureState ? structureState.selected : false;
            $scope.sharedStructure.types = [];
            $scope.resourceTypes.all.forEach(function (resource) {
                var find = _.findWhere($scope.structures, {id: resource.school_id});
                if (!find) {
                    $scope.sharedStructure.types.push(resource);
                }
            });
        };
        $scope.initStructures = function () {
            $scope.notificationsComponent.getNotifications(function (data) {
                $scope.notificationsComponent.list = data;
                model.loadTreeState(function (state) {
                    for (var i = 0; i < $scope.structures.length; i++) {
                        var structureState = state.find(function (struct) {
                            return struct.id === $scope.structures[i].id
                        });
                        var structureWithTypes: any = {};
                        structureWithTypes.id = $scope.structures[i].id;
                        structureWithTypes.expanded = structureState ? structureState.expanded : false;
                        structureWithTypes.selected = structureState ? structureState.selected : false;
                        structureWithTypes.types = [];
                        structureWithTypes.name = $scope.structures[i].name;
                        $scope.resourceTypes.forEach(function (resourceType) {
                            resourceType.resources.all.forEach(function (resource) {
                                resource.notified = $scope.notificationsComponent.list.some(function (res) {
                                    return res.resource_id == resource.id
                                });
                            });
                            $scope.checkNotificationsResourceType(resourceType);
                            var typeState = structureState
                                ? structureState.types.find(function (type) {
                                    return type.id === resourceType.id
                                })
                                : undefined;

                            if (typeState) {
                                resourceType.expanded = typeState.expanded;

                                resourceType.resources.all.forEach(function (resource) {
                                    var resState = typeState.resources.find(function (res) {
                                        return res.id === resource.id
                                    });

                                    if (resState) {
                                        resource.selected = resState.selected;
                                    }
                                })
                            }
                            if (state.length == 0) {
                                resourceType.resources.deselectAll();
                            }

                            if (resourceType.school_id === $scope.structures[i].id) {
                                structureWithTypes.types.push(resourceType);
                            }
                        });
                        $scope.structuresWithTypes[i] = structureWithTypes;
                    }
                    $scope.structuresWithTypes.sort(
                        ArrayUtil.sort_by('name', false, function (a) {
                            return a.toUpperCase();
                        })
                    );
                    $scope.selectedStructure = $scope.structuresWithTypes[0];
                    model.bookings.applyFilters();
                    $scope.getSharedResources(state);
                });
            });
        };

        $scope.initStructuresManage = function (selected, currentResourceType) {
            var thisResourceType = {};
            for (var i = 0; i < $scope.structures.length; i++) {
                var structureWithTypes: any = {};
                structureWithTypes.id = $scope.structures[i].id;
                structureWithTypes.expanded = true;
                structureWithTypes.selected = selected;
                structureWithTypes.types = [];
                structureWithTypes.name = $scope.structures[i].name;
                $scope.resourceTypes.forEach(function (resourceType) {
                    if (resourceType.school_id === $scope.structures[i].id) {
                        structureWithTypes.types.push(resourceType);
                    }
                    if (currentResourceType && resourceType.id === currentResourceType.id) {
                        thisResourceType = resourceType;
                    }
                });
                $scope.structuresWithTypes[i] = structureWithTypes;
            }
            $scope.structuresWithTypes.sort(
                ArrayUtil.sort_by('name', false, function (a) {
                    return a.toUpperCase();
                })
            );
            if (currentResourceType) {
                $scope.selectResourceType(thisResourceType);
            } else {
                $scope.setSelectedStructureForCreation($scope.structuresWithTypes[0]);
            }

        }

        $scope.deleteTypesInStructures = function () {
            for (var i = 0; i < $scope.structures.length; i++) {
                if ($scope.structuresWithTypes[i] !== undefined) {
                    $scope.structuresWithTypes[i].types = [];
                }
            }
        };

        $scope.initResourcesRouted = function (bookingId, cb) {
            var found = false;
            var actions = 0;
            var routedBooking = undefined;
            var countTotalTypes = $scope.resourceTypes.length();
            $scope.resourceTypes.forEach(function (resourceType) {
                countTotalTypes--; // premier
                var countTotalResources = resourceType.resources.length();
                actions = actions + resourceType.resources.size();
                resourceType.resources.forEach(function (resource) {
                    countTotalResources--;
                    resource.bookings.sync(function () {
                        if (routedBooking !== undefined || found) {
                            return;
                        }
                        routedBooking = resource.bookings.find(function (booking) {
                            return booking.id == bookingId;
                        });
                        if (routedBooking !== undefined) {
                            $scope.bookings.pushAll(routedBooking.resource.bookings.all);
                            var struct = $scope.structures.find(s => s.id === routedBooking.resource.type.structure.id);
                            struct.expanded = true;
                            routedBooking.resource.type.expanded = true;
                            routedBooking.resource.selected = true;
                            $scope.viewBooking(routedBooking);
                            $scope.display.routed = undefined;
                            model.recordedSelections.firstResourceType = undefined;
                            updateCalendarSchedule(model.bookings.startPagingDate);
                            found = true;
                        }
                        if (
                            countTotalTypes == 0 &&
                            countTotalResources == 0 &&
                            typeof cb === 'function'
                        ) {
                            if (!found) {
                                // error
                                console.error('Booking not found (id: ' + bookingId + ')');
                                notify.error('rbs.route.booking.not.found');
                                $scope.display.routed = undefined;
                                $scope.initResources();
                            }
                            cb();
                        }
                    });
                });
            });
        };

        // Navigation
        $scope.showList = function (refresh) {
            if (refresh === true) {
                $scope.initMain();
            }
            $scope.display.admin = false;
            $scope.display.list = true;
            $scope.bookings.filters.booking = true;
            $scope.bookings.syncForShowList();
            $scope.bookings.applyFilters();
            template.open('bookings', 'main-list');
            $scope.safeApply();
        };

        $scope.showCalendar = function (refresh) {
            if (refresh === true) {
                $scope.initMain();
            }
            $scope.display.admin = false;
            $scope.display.list = false;
            $scope.bookings.filters.booking = undefined;
            $scope.bookings.applyFilters();
            template.open('bookings', 'main-calendar');
            CalendarUtil.placingButton($timeout, $scope, 0);
            $scope.safeApply();
        };

        $scope.showManage = function () {
            $scope.display.list = undefined;
            $scope.display.admin = true;
            $scope.resourceTypes.deselectAllResources();

            var processableResourceTypes = _.filter(
                $scope.resourceTypes.all,
                $scope.keepProcessableResourceTypes
            );
            if (processableResourceTypes && processableResourceTypes.length > 0) {
                $scope.currentResourceType = processableResourceTypes[0];
            }
            $scope.initStructuresManage(false);
            template.open('main', 'manage-view');
            template.open('resources', 'resource/manage-resources');
        };

        $scope.initMain = function () {
            //fixme Why model.recordedSelections.firstResourceType = true;
            model.recordedSelections.allResources = true;
            $scope.currentResourceType = undefined;
            $scope.resetSort();
            model.refresh($scope.display.list);
            template.open('main', 'main-view');
        };

        // Main view interaction
        $scope.expandResourceType = function (resourceType) {
            resourceType.expanded = true;
            $scope.selectResources(resourceType);
            $scope.saveTreeState();
        };

        $scope.expandStructure = function (structure) {
            structure.expanded = true;
            $scope.saveTreeState();
        };

        $scope.expandStructureSettings = function (structure) {
            structure.expanded = true;
            structure.selected = false;
            structure.types.forEach(function (type) {
                type.selected = false;
            });
        };

        $scope.collapseResourceType = function (resourceType, needToSaveTreeState) {
            resourceType.expanded = undefined;
            $scope.deselectResources(resourceType);
            if (needToSaveTreeState !== false) {
                $scope.saveTreeState();
            }
        };

        $scope.collapseStructure = function (structure) {
            structure.expanded = undefined;
            structure.types.forEach(function (resourceType) {
                $scope.collapseResourceType(resourceType, false);
            });
            $scope.deselectStructure(structure);
            $scope.saveTreeState();
        };

        $scope.collapseStructureSettings = function (structure) {
            structure.expanded = undefined;
            $scope.deselectStructureSettings(structure);
        };

        $scope.selectResources = function (resourceType) {
            resourceType.resources.forEach(function (resource) {
                if (resource.selected !== true) {
                    resource.selected = true;
                }
            });
            $scope.lastSelectedResource = resourceType.resources.first();
            model.bookings.applyFilters();
        };

        $scope.selectStructure = function (structure) {
            structure.selected = true;
            structure.types.forEach(function (type) {
                type.expanded = true;
                $scope.selectResources(type);
            });
        };

        $scope.setSelectedStructureForCreation = function (structure) {
            if ($scope.selectedStructure) {
                var oldStructure = $scope.selectedStructure;
                if (oldStructure != structure) {
                    oldStructure.types.forEach(function (resourceType) {
                        resourceType.selected = undefined;
                    });
                }
            }
            $scope.selectedStructure = structure;
            var type = $scope.filteredType(structure);
            if (type.length > 0) {
                $scope.selectResourceType(type[0]);
            } else {
                $scope.currentResourceType = undefined;
                template.close('resources');
            }
        };

        $scope.selectStructureSettings = function (structure) {
            structure.selected = true;
            structure.types.forEach(function (type) {
                type.selected = true;
            });
        };

        $scope.deselectResources = function (resourceType) {
            resourceType.resources.forEach(function (resource) {
                resource.selected = undefined;
            });
            $scope.lastSelectedResource = undefined;
            model.bookings.applyFilters();
        };

        $scope.deselectStructure = function (structure) {
            structure.selected = false;
            structure.types.forEach(function (type) {
                $scope.deselectResources(type);
            });
        };

        $scope.deselectStructureSettings = function (structure) {
            structure.selected = false;
            structure.types.forEach(function (type) {
                $scope.deselectTypeResourcesSettings(type);
            });
            if ($scope.selectedStructure === structure) {
                $scope.selectedStructure = undefined;
                $scope.currentResourceType = undefined;
                template.close('resources');
            }
        };

        $scope.deselectTypeResourcesSettings = function (resourceType) {
            resourceType.selected = false;
            resourceType.resources.forEach(function (resource) {
                resource.selected = undefined;
            });
        };

        $scope.switchSelectResources = function (resourceType) {
            if (
                resourceType.resources.every(function (resource) {
                    return resource.selected;
                })
            ) {
                $scope.deselectResources(resourceType);
            } else {
                $scope.selectResources(resourceType);
            }

            $scope.saveTreeState();
        };

        $scope.switchSelectStructure = function (structure) {
            if (structure.selected) {
                $scope.deselectStructure(structure);
            } else {
                $scope.selectStructure(structure);
            }
            $scope.saveTreeState();
        };

        $scope.switchSelectStructureSettings = function (structure) {
            if (structure.selected) {
                $scope.deselectStructureSettings(structure);
            } else {
                $scope.selectStructureSettings(structure);
            }

            $scope.saveTreeState();
        };

        $scope.switchSelect = function (resource) {
            if (resource.selected !== true) {
                resource.selected = true;
                if (resource.is_available !== true) {
                    $scope.lastSelectedResource = resource;
                }
                model.bookings.applyFilters();
            } else {
                resource.selected = undefined;
                if (resource.is_available !== true) {
                    $scope.lastSelectedResource = undefined;
                }
                model.bookings.applyFilters();
            }

            $scope.saveTreeState();
        };

        $scope.switchSelectMine = function () {
            if ($scope.bookings.filters.mine === true) {
                delete $scope.bookings.filters.mine;
            } else {
                $scope.bookings.filters.mine = true;
                delete $scope.bookings.filters.unprocessed;
            }
            $scope.bookings.applyFilters();
        };

        $scope.switchSelectUnprocessed = function () {
            if ($scope.bookings.filters.unprocessed === true) {
                delete $scope.bookings.filters.unprocessed;
            } else {
                $scope.bookings.filters.unprocessed = true;
                delete $scope.bookings.filters.mine;
            }
            $scope.bookings.applyFilters();
        };

        $scope.isViewBooking = false;
        // Bookings
        $scope.viewBooking = function (booking) {
            // booking = semanticObject(booking, Booking);

            // Booking's type model is ScheduleItem so we reassign correct Model (Booking and Resource) to currentBookingSelected
            $scope.currentBookingSelected = new Booking(booking);
            $scope.currentBookingSelected.resource = new Resource(booking.resource);

            $scope.isViewBooking = true;
            if (booking.isSlot()) {
                // slot : view booking details and show slot
                //call back-end to obtain all periodic slots
                if (booking.booking.occurrences !== booking.booking._slots.length) {
                    $scope.bookings.loadSlots(booking, function () {
                        if (booking.status === $scope.status.STATE_REFUSED) {
                            booking.expanded = true;
                        }
                        $scope.showBookingDetail(booking.booking, 3);
                    });
                } else {
                    if (booking.status === $scope.status.STATE_REFUSED) {
                        booking.expanded = true;
                    }
                    $scope.showBookingDetail(booking.booking, 3);
                }
            } else {
                // booking
                $scope.showBookingDetail(booking, 1);
            }
        };

        $scope.showBookingDetail = function (booking, displaySection) {
            booking = $scope.bookings.find(b => b.id === booking.id);
            $scope.selectedBooking = booking;
            $scope.selectedBooking.displaySection = displaySection;
            $scope.initModerators();
            $scope.display.showPanel = true;
            template.open('lightbox', 'booking/booking-details');
            $scope.safeApply();
        };

        $scope.closeBooking = function () {
            $scope.slotNotFound = undefined;
            if (
                $scope.selectedBooking !== undefined &&
                $scope.selectedBooking.is_periodic === true
            ) {
                _.each($scope.selectedBooking._slots, function (slot) {
                    slot.expanded = false;
                });
            }
            if ($scope.display.list !== true) {
                // In calendar view, deselect all when closing lightboxes
                $scope.bookings.deselectAll();
                $scope.bookings.applyFilters();
            }
            $scope.selectedBooking = undefined;
            $scope.editedBooking = null;
            $scope.processBookings = [];
            $scope.currentErrors = [];
            $scope.display.showPanel = false;
            $scope.slots = undefined;
            template.close('lightbox');
        };

        $scope.expandPeriodicBooking = function (booking) {
            booking.expanded = true;
            booking.showSlots();
        };

        $scope.collapsePeriodicBooking = function (booking) {
            booking.expanded = undefined;
            booking.hideSlots();
        };

        $scope.switchSelectAllBookings = function () {
            if ($scope.display.selectAllBookings) {
                $scope.bookings.selectAllBookings();
            } else {
                $scope.bookings.deselectAll();
            }
        };

        $scope.switchSelectAllSlots = function (booking) {
            BookingUtil.setRightsResourceBooking(booking);
            if (booking.is_periodic === true && booking.selected === true) {
                _.each(booking._slots, function (slot) {
                    slot.selected = true;
                });
            } else if (booking.is_periodic === true && booking.selected !== true) {
                _.each(booking._slots, function (slot) {
                    slot.selected = undefined;
                });
            }
        };

        // Sort
        $scope.switchSortBy = function (predicate) {
            if (predicate === $scope.sort.predicate) {
                $scope.sort.reverse = !$scope.sort.reverse;
            } else {
                $scope.sort.predicate = predicate;
                $scope.sort.reverse = false;
            }
        };

        $scope.resetSort = function () {
            $scope.sort.predicate = 'start_date';
            $scope.sort.reverse = false;
        };

        $scope.switchFilterListByDates = function (filter) {
            if ($scope.bookings.filters.dates !== true || filter === true) {
                $scope.bookings.filters.startMoment = moment(
                    $scope.bookings.filters.startDate
                );
                $scope.bookings.filters.endMoment = moment(
                    $scope.bookings.filters.endDate
                );
                $scope.bookings.filters.dates = true;
            } else {
                $scope.bookings.filters.dates = undefined;
            }
            $scope.bookings.applyFilters();
            $scope.bookings.trigger('change');
            $scope.safeApply();
        };

        // General
        $scope.formatTypeDate = function (date) {
            return $scope.formatMoment(moment(date));
        };

        $scope.formatDateLong = function (date) {
            return $scope.formatMomentLong(moment(date));
        };

        $scope.formatMoment = function (date) {
            return (
                date.format('DD/MM/YYYY ') +
                lang.translate('rbs.booking.details.header.at') +
                date.format(' H[h]mm')
            );
        };

        $scope.formatMomentLong = function (date) {
            return date.locale(window.navigator.language).format('dddd DD MMMM YYYY - HH[h]mm');
        };

        $scope.formatMomentDayMedium = function (date) {
            return date.locale(window.navigator.language).format('dddd DD MMM YYYY');
        };

        $scope.dateToSeconds = function (date) {
            var momentDate = moment(date);
            return moment
                .utc([
                    momentDate.year(),
                    momentDate.month(),
                    momentDate.day(),
                    momentDate.hour(),
                    momentDate.minute(),
                ])
                .unix();
        };

        // Booking edition
        $scope.canCreateBooking = function () {
            if (
                undefined !==
                $scope.resourceTypes.find(function (resourceType) {
                    return (
                        resourceType.myRights !== undefined &&
                        resourceType.myRights.contrib !== undefined
                    );
                })
            ) {
                return true;
            }
            return false;
        };

        $scope.canDeleteBookingSelection = function () {
            if ($scope.display.list === true) {
                return _.every($scope.bookings.selection(), function (booking) {
                    return (
                        booking.isBooking() ||
                        (isBookingSlot(booking) && booking.booking.selected === true)
                    );
                });
            } else {
                return true;
            }
        };

        /**
         * Trick that will use the "selected" field from ScheduleItem (calendar model)
         * for setting our "selected" field from the model Booking
         *
         * @param item: any but actually is ScheduleItem from calendar item
         */
        $scope.setSelectedItem = (item: any): void => {
            $scope.bookings.filtered.find(b => b.id === item.id).selected = item.selected;
        };

        $scope.canDeleteBookingDateCheck = function (dateToCheck) {
            var itemDate = moment(dateToCheck);
            if (moment().diff(itemDate) <= 0) {
                return true;
            } else {
                return false;
            }
        };

        $scope.newBooking = (): void => {
            window.bookingState = BOOKING_EVENTER.CREATE;
            template.open('lightbox', 'booking/edit-booking');
            $scope.display.showPanel = true;
        };

        $scope.editBooking = (): void => {
            window.bookingState = BOOKING_EVENTER.EDIT;
            template.open('lightbox', 'booking/edit-booking');
            $scope.display.showPanel = true;
        };

        $scope.initBookingDates = function (startMoment, endMoment) {
            // hours minutes management
            var minTime = moment(startMoment);
            minTime.set('hour', model.timeConfig.start_hour);
            var maxTime = moment(endMoment);
            maxTime.set('hour', model.timeConfig.end_hour);
            if (startMoment.isAfter(minTime) && startMoment.isBefore(maxTime)) {
                $scope.booking.startTime = startMoment;
                if ($scope.selectedSlotStart) {
                    $scope.booking.startTime.set('hour', $scope.selectedSlotStart.startHour.split(':')[0]);
                    $scope.booking.startTime.set('minute',$scope.selectedSlotStart.startHour.split(':')[1]);
                }
            } else {
                $scope.booking.startTime = minTime;
                if (startMoment.isAfter(maxTime)) {
                    startMoment.add('day', 1);
                    endMoment.add('day', 1);
                    maxTime.add('day', 1);
                }
            }
            if (endMoment.isBefore(maxTime)) {
                $scope.booking.endTime = endMoment;
                if ($scope.selectedSlotStart) {
                    $scope.booking.endTime.set('hour', $scope.selectedSlotStart.endHour.split(':')[0]);
                    $scope.booking.endTime.set('minute', $scope.selectedSlotStart.endHour.split(':')[1]);
                }
            } else {
                $scope.booking.endTime = maxTime;
            }

            // dates management

            // setter startDate booking
            $scope.booking.startDate = startMoment.toDate();
            $scope.booking.startDate.setFullYear(startMoment.year());
            $scope.booking.startDate.setMonth(startMoment.month());
            $scope.booking.startDate.setDate(startMoment.date());

            // setter endDate booking
            $scope.booking.endDate = endMoment.toDate();
            $scope.booking.endDate.setFullYear(endMoment.year());
            $scope.booking.endDate.setMonth(endMoment.month());
            $scope.booking.endDate.setDate(endMoment.date());

            // setter periodicEndDate
            $scope.booking.periodicEndDate = endMoment.toDate();
        };

        $scope.toggleShowBookingResource = function () {
            if ($scope.editedBooking.showResource == true) {
                $scope.editedBooking.showResource = undefined;
            } else {
                $scope.editedBooking.showResource = true;
            }
        };

        $scope.removeBookingSelection = function () {
            $scope.display.processing = undefined;
            if ($scope.selectedBooking !== undefined) {
                $scope.bookings.deselectAll();
                $scope.selectedBooking.selected = true;
            }

            var totalSelectionAsynchroneCall = 0;

            $scope.bookings.selection().forEach(booking => {
                if (!$scope.isViewBooking) {
                    $scope.currentBookingSelected = booking;
                }
                if (isBookingSlot(booking) && booking.booking.occurrences !== booking.booking._slots.length) {
                    totalSelectionAsynchroneCall++;
                } else if (isBookingSlot(booking) && booking.booking.selected !== true) {
                    booking.booking.selected = true;
                    booking.booking.selectAllSlots();
                } else if (booking.is_periodic) {
                    booking.selectAllSlots();
                    // trick to refresh correctly `selectAllSlots`'s field select methods
                    const currentBookingSlotsMap: Map<number, boolean> = new Map(booking._slots.map(s => [s.id, s.selected]));
                    $scope.bookings.all.forEach(booking => {
                        if (currentBookingSlotsMap.has(booking.id)) {
                            booking.selected = currentBookingSlotsMap.get(booking.id);
                        }
                    });
                }
            });


            //if all slots are already completed
            if (totalSelectionAsynchroneCall === 0) {
                //confirm message
                if (
                    isBookingSlot($scope.currentBookingSelected) &&
                    $scope.currentBookingSelected.booking._slots.length !== 1
                ) {
                    $scope.isViewBooking = false;
                    $scope.showDeletePeriodicBookingMessage();
                } else if (
                    isBookingSlot($scope.currentBookingSelected) &&
                    $scope.currentBookingSelected.booking._slots.length === 1
                ) {
                    $scope.isViewBooking = false;
                    $scope.showConfirmDeleteMessage();
                } else {
                    $scope.showConfirmDeleteMessage();
                }
            } else {
                // All slots for periodic bookings
                _.each($scope.bookings.selection(), function (booking) {
                    if (!$scope.isViewBooking) {
                        $scope.currentBookingSelected = booking;
                    }
                    if (
                        isBookingSlot(booking) &&
                        booking.booking.occurrences !== booking.booking._slots.length
                    ) {
                        //call back-end to obtain all periodic slots
                        $scope.bookings.loadSlots(booking, function () {
                            booking.booking.selected = true;
                            booking.booking.selectAllSlots();
                            totalSelectionAsynchroneCall--;
                            if (totalSelectionAsynchroneCall === 0) {
                                $scope.isViewBooking = false;
                                if (
                                    isBookingSlot($scope.currentBookingSelected) &&
                                    $scope.currentBookingSelected.booking._slots.length !== 1
                                ) {
                                    $scope.showDeletePeriodicBookingMessage();
                                } else if (
                                    isBookingSlot($scope.currentBookingSelected) &&
                                    $scope.currentBookingSelected.booking._slots.length === 1
                                ) {
                                    $scope.showConfirmDeleteMessage();
                                }
                            }
                        });
                    }
                });
            }
        };

        $scope.showConfirmDeleteMessage = function () {
            $scope.processBookings = $scope.bookings.selectionForProcess();
            if (!$scope.processBookings.length) {
                $scope.processBookings = $scope.selectBooking($scope.selectedBooking);
            }
            template.open('lightbox', 'booking/confirm-delete-booking');
            $scope.display.showPanel = true;
            $scope.safeApply();
        };

        $scope.showDeletePeriodicBookingMessage = function () {
            $scope.processBookings = $scope.bookings.selectionForProcess();
            if (!$scope.processBookings.length) {
                $scope.processBookings = $scope.selectBooking($scope.selectedBooking);
            }
            template.open('lightbox', 'booking/delete-periodic-booking');
            $scope.display.showPanel = true;
            $scope.safeApply();
        };

        $scope.doRemoveCurrentPeriodicBookingSelection = function () {
            $scope.display.processing = true;
            $scope.currentErrors = [];
            try {
                $scope.currentBookingSelected.delete(
                    function () {
                        $scope.display.processing = undefined;
                        $scope.bookings.deselectAll();
                        $scope.closeBooking();
                        model.refreshBookings($scope.display.list);
                    },
                    function (e) {
                        $scope.currentErrors.push(e);
                        $scope.display.processing = undefined;
                        $scope.showActionErrors();
                        model.refreshBookings($scope.display.list);
                    }
                );
            } catch (e) {
                $scope.display.processing = undefined;
                $scope.currentErrors.push({error: 'rbs.error.technical'});
            }
        };

        $scope.doRemoveCurrentAndFutureBookingSelection = (): void => {
            $scope.display.processing = true;
            $scope.currentErrors = [];
            try {
                $scope.currentBookingSelected.deletePeriodicCurrentToFuture(
                    function () {
                        $scope.display.processing = undefined;
                        $scope.bookings.deselectAll();
                        $scope.closeBooking();
                        model.refreshBookings($scope.display.list);
                    },
                    function (e) {
                        $scope.currentErrors.push(e);
                        $scope.display.processing = undefined;
                        $scope.showActionErrors();
                        model.refreshBookings($scope.display.list);
                    }
                );
            } catch (e) {
                console.error(e);
                $scope.display.processing = undefined;
                $scope.currentErrors.push({error: 'rbs.error.technical'});
            }
        };

        $scope.hasResourceRight = function (resource, right) {
            return !(!resource.myRights || resource.myRights[right] === undefined);
        }

        $scope.validateBookingSelection = function () {
            $scope.display.processing = undefined;
            if ($scope.selectedBooking !== undefined) {
                $scope.bookings.deselectAll();
                if ($scope.selectedBooking.is_periodic === true) {
                    $scope.selectedBooking.selectAllSlots();
                    $scope.selectedBooking.selected = undefined;
                } else {
                    $scope.selectedBooking.selected = true;
                }
            }

            $scope.processBookings = $scope.selectedBooking && '_slots' in $scope.selectedBooking ? $scope.selectedBooking._slots : $scope.bookings.selectionForProcess();
            if (!$scope.processBookings.length) {
                $scope.processBookings = $scope.selectBooking($scope.selectedBooking);
            }
            $scope.display.showPanel = true;
            template.open('lightbox', 'booking/validate-booking');
        };

        $scope.refuseBookingSelection = function () {
            $scope.display.processing = undefined;
            if ($scope.selectedBooking !== undefined) {
                $scope.bookings.deselectAll();
                if ($scope.selectedBooking.is_periodic === true) {
                    $scope.selectedBooking.selectAllSlots();
                    $scope.selectedBooking.selected = undefined;
                } else {
                    $scope.selectedBooking.selected = true;
                }
            }

            $scope.processBookings = $scope.selectedBooking && '_slots' in $scope.selectedBooking ? $scope.selectedBooking._slots : $scope.bookings.selectionForProcess();
            if (!$scope.processBookings.length) {
                $scope.processBookings = $scope.selectBooking($scope.selectedBooking);
            }
            $scope.display.showPanel = true;
            $scope.bookings.refuseReason = '';
            template.open('lightbox', 'booking/refuse-booking');
        };

        // Management view interaction
        $scope.selectResourceType = function (resourceType) {
            $scope.resourceTypes.deselectAllResources();
            $scope.display.selectAllRessources = undefined;
            $scope.currentResourceType = resourceType;
            var oldStructure = $scope.selectedStructure;
            $scope.selectedStructure = $scope.structuresWithTypes.filter(function (struct) {
                return struct.id === resourceType.school_id
            }).pop();
            if (!$scope.selectedStructure) {
                $scope.selectedStructure = $scope.sharedStructure;
            }
            if (oldStructure != $scope.selectedStructure) {
                oldStructure.types.forEach(function (resourceType) {
                    resourceType.selected = undefined;
                });
            }
            if ($scope.editedResourceType !== undefined) {
                $scope.closeResourceType();
            }

            template.open('resources', 'resource/manage-resources');
        };

        $scope.swicthSelectAllRessources = function () {
            if ($scope.display.selectAllRessources) {
                $scope.currentResourceType.resources.selectAll();
            } else {
                $scope.currentResourceType.resources.deselectAll();
            }
        };

        $scope.switchExpandResource = function (resource) {
            if (resource.expanded === true) {
                resource.expanded = undefined;
            } else {
                resource.expanded = true;
            }
        };
        $scope.filteredType = function (structure) {
            return _.filter(structure.types, function (type) {
                return $scope.keepProcessableResourceTypes(type)
            });
        };
        $scope.createResourceType = function () {
            $scope.display.processing = undefined;
            $scope.editedResourceType = new RBS.ResourceType();
            $scope.editedResourceType.validation = false;
            $scope.editedResourceType.color = model.getNextColor();
            $scope.editedResourceType.structure = $scope.selectedStructure;
            $scope.editedResourceType.slotprofile = null;
            $scope.updateSlotProfileField($scope.selectedStructure);
            template.open('resources', 'resource/edit-resource-type');
        };

        $scope.newResource = function () {
            $scope.isCreation = true;
            $scope.display.processing = undefined;
            $scope.editedResource = new Resource();
            $scope.editedResource.type = $scope.currentResourceType;
            $scope.editedResource.color = $scope.currentResourceType.color;
            $scope.editedResource.validation = $scope.currentResourceType.validation;
            $scope.editedResource.is_available = true;
            $scope.editedResource.periodic_booking = true;
            $scope.editedResource.quantity = 1;
            $scope.editedResource.availabilities = new Availabilities();
            $scope.editedResource.unavailabilities = new Availabilities();
            $scope.openAvailabilitiesTable = false;
            template.open('resources', 'resource/edit-resource');
        };

        $scope.editSelectedResource = async () : Promise<void> => {
            // $scope.bookings.sync();
            $scope.currentErrors = [];
            $scope.bookings.syncForShowList();
            $scope.isCreation = false;
            $scope.display.processing = undefined;
            $scope.editedResource = $scope.currentResourceType.resources.all.filter(r => r.selected)[0];
            $scope.currentResourceType.resources.deselectAll();
            if ($scope.editedResource.quantity === undefined) {
                $scope.editedResource.quantity = 1;
            }
            await $scope.editedResource.syncResourceAvailabilities();
            $scope.syncBookingsUsingResource($scope.editedResource, $scope.bookingsConflictingResource, $scope.bookingsOkResource);
            $scope.displayLightbox.saveQuantity = false;
            $scope.displayLightbox.saveAvailabilityResource = false;
            $scope.openAvailabilitiesTable = $scope.editedResource.availabilities.all.length > 0 || $scope.editedResource.unavailabilities.all.length > 0;
            if ($scope.openAvailabilitiesTable) $scope.initEditedAvailability();

            // Field to track Resource availability change
            $scope.editedResource.was_available = $scope.editedResource.is_available;

            $scope.editedResource.hasMaxDelay =
                $scope.editedResource.max_delay !== undefined &&
                $scope.editedResource.max_delay !== null;
            $scope.editedResource.hasMinDelay =
                $scope.editedResource.min_delay !== undefined &&
                $scope.editedResource.min_delay !== null;
            template.open('resources', 'resource/edit-resource');
        };

        $scope.shareCurrentResourceType = function () {
            $scope.display.showPanel = true;
        };

        $scope.saveResourceType = function () {
            $scope.display.processing = true;
            $scope.currentErrors = [];
            $scope.isManage = true;
            $scope.editedResourceType.save(
                function () {
                    $scope.display.processing = undefined;
                    $scope.currentResourceType = $scope.editedResourceType;
                    $scope.closeResourceType();
                    model.refreshRessourceType();
                },
                function (e) {
                    $scope.currentErrors.push(e);
                    $scope.display.processing = undefined;
                    $scope.safeApply();
                }
            );
        };

        $scope.saveResource = function () {
            if ($scope.bookingsConflictingResource.length > 0) {
                $scope.displayLightbox.saveQuantity = true;
                $scope.safeApply();
            }
            else {
                $scope.doSaveResource();
            }
        };

        $scope.doSaveResource = async function () {
            $scope.display.processing = true;
            $scope.isManage = true;
            if ($scope.editedResource.is_available === 'true') {
                $scope.editedResource.is_available = true;
            }
            else if ($scope.editedResource.is_available === 'false') {
                $scope.editedResource.is_available = false;
            }

            $scope.currentErrors = [];
            $scope.editedResource.save(
                function () {
                    $scope.display.processing = undefined;
                    $scope.closeResource();
                    model.refreshRessourceType();
                },
                function (e) {
                    $scope.currentErrors.push(e);
                    $scope.display.processing = undefined;
                    $scope.safeApply();
                }
            );

            // Deals with (un)availabilities to keep/delete
            if ($scope.editedResource.was_available != $scope.editedResource.is_available) {
                await availabilityService.deleteAll($scope.editedResource.id, !$scope.editedResource.is_available);
            }
            else {
                let listToUpdate = $scope.editedResource.is_available ?
                    $scope.editedResource.unavailabilities.all : $scope.editedResource.availabilities.all;

                for (let availability of listToUpdate) {
                    if (availability.quantity > $scope.editedResource.quantity) {
                        availability.quantity = $scope.editedResource.quantity;
                        await availabilityService.update(availability);
                    }
                }
            }

            // Deals with bookings validation system
            $scope.treatBookings($scope.editedResource, $scope.bookingsConflictingResource, $scope.bookingsOkResource);
            $scope.$apply();
        };

        $scope.deleteResourcesSelection = function () {
            $scope.currentResourceType.resourcesToDelete = $scope.currentResourceType.resources.selection();
            $scope.currentResourceType.resources.deselectAll();
            template.open('resources', 'resource/confirm-delete-resource');
        };

        $scope.doDeleteResource = function () {
            $scope.isManage = true;
            $scope.display.processing = true;
            $scope.currentErrors = [];
            var actions = $scope.currentResourceType.resourcesToDelete.length;
            _.each($scope.currentResourceType.resourcesToDelete, function (resource) {
                resource.delete(
                    function () {
                        actions--;
                        if (actions === 0) {
                            $scope.display.processing = undefined;
                            $scope.closeResource();
                            model.refreshRessourceType();
                        }
                    },
                    function (e) {
                        $scope.currentErrors.push(e);
                        actions--;
                        if (actions === 0) {
                            $scope.display.processing = undefined;
                            $scope.showActionErrors();
                            model.refreshRessourceType();
                        }
                    }
                );
            });
        };

        $scope.closeResourceType = function () {
            $scope.editedResourceType = undefined;
            $scope.currentErrors = [];
            if ($scope.display.showPanel === true) {
                $scope.display.showPanel = false;
                template.close('lightbox');
            }
            template.open('resources', 'resource/manage-resources');
        };

        $scope.closeResource = function () {
            $scope.editedResource = undefined;
            $scope.currentErrors = [];
            $scope.display.showPanel = false;
            template.close('lightbox');
            template.open('resources', 'resource/manage-resources');
        };

        // Errors
        $scope.showActionErrors = function () {
            $scope.display.showPanel = true;
            template.open('lightbox', 'action-errors');
        };

        $scope.isErrorObjectResourceType = function (object) {
            return object instanceof ResourceType;
        };

        $scope.isErrorObjectResource = function (object) {
            return object instanceof Resource;
        };

        $scope.isErrorObjectBooking = function (object) {
            return object instanceof Booking;
        };

        $scope.closeActionErrors = function () {
            $scope.display.showPanel = false;
            template.close('lightbox');
        };

        // Special Workflow and Behaviours
        $scope.hasWorkflowOrAnyResourceHasBehaviour = function (
            workflowRight,
            ressourceRight
        ) {
            var workflowRights = workflowRight.split('.');
            return (
                (model.me.workflow[workflowRights[0]] !== undefined &&
                    model.me.workflow[workflowRights[0]][workflowRights[1]] === true) ||
                model.resourceTypes.find(function (resourceType) {
                    return (
                        resourceType.resources.find(function (resource) {
                            return (
                                resource.myRights !== undefined &&
                                resource.myRights[ressourceRight] !== undefined
                            );
                        }) !== undefined
                    );
                })
            );
        };

        // Used when adding delays to resources
        $scope.delayDays = _.range(1, 31);
        $scope.daysToSeconds = function (nbDays) {
            return moment.duration(nbDays, 'days').asSeconds();
        };
        $scope.secondsToDays = function (nbSeconds) {
            return moment.duration(nbSeconds, 'seconds').asDays();
        };

        // Get Moderators
        $scope.initModerators = function () {
            if ($scope.resourceTypes.first() === undefined || $scope.resourceTypes.first().moderators === undefined) {
                $scope.resourceTypes.forEach(function (resourceType) {
                    resourceType.getModerators(function () {
                        $scope.$apply('resourceTypes');
                    });
                });
            }
        };

        $scope.nextWeekButton = function () {
            model.calendar.next();
            var calendarMode = model.calendar.increment;
            var next = undefined;
            switch (calendarMode) {
                case 'month':
                    next = moment(model.calendar.firstDay);
                    model.bookings.startPagingDate = moment(model.calendar.firstDay).clone().startOf('month');
                    model.bookings.endPagingDate = moment(model.calendar.firstDay).clone().endOf('month');
                    break;
                case 'week':
                    next = moment(model.calendar.firstDay).add(7, 'day');
                    model.bookings.startPagingDate = moment(model.bookings.startPagingDate).add(7, 'day');
                    model.bookings.endPagingDate = moment(model.bookings.endPagingDate).add(7, 'day');
                    break;
                case 'day':
                    next = moment(model.calendar.firstDay).add(1, 'day');
                    model.bookings.startPagingDate = moment(model.bookings.startPagingDate).add(1, 'day');
                    model.bookings.endPagingDate = moment(model.bookings.endPagingDate).add(1, 'day');
                    break;
            }

            updateCalendarSchedule(next);
        };

        $scope.previousWeekButton = function () {
            model.calendar.previous();
            var calendarMode = model.calendar.increment;
            var prev = undefined;
            switch (calendarMode) {
                case 'month':
                    prev = moment(model.calendar.firstDay);
                    model.bookings.startPagingDate = moment(model.calendar.firstDay).clone().startOf('month');
                    model.bookings.endPagingDate = moment(model.calendar.firstDay).clone().endOf('month');
                    break;
                case 'week':
                    prev = moment(model.calendar.firstDay).subtract(7, 'day');
                    model.bookings.startPagingDate = moment(model.bookings.startPagingDate).subtract(7, 'day');
                    model.bookings.endPagingDate = moment(model.bookings.endPagingDate).subtract(7, 'day');
                    break;
                case 'day':
                    prev = moment(model.calendar.firstDay).subtract(1, 'day');
                    model.bookings.startPagingDate = moment(model.bookings.startPagingDate).subtract(1, 'day');
                    model.bookings.endPagingDate = moment(model.bookings.endPagingDate).subtract(1, 'day');
                    break;
            }

            updateCalendarSchedule(prev);
        };

        $scope.nextWeekBookingButton = function () {
            var nextStart = moment(model.bookings.filters.startMoment).add(7, 'day');
            var nextEnd = moment(model.bookings.filters.endMoment).add(7, 'day');
            updateCalendarList(nextStart, nextEnd);
        };

        $scope.previousWeekBookingButton = function () {
            var prevStart = moment(model.bookings.filters.startMoment).subtract(7, 'day');
            var prevEnd = moment(model.bookings.filters.endMoment).subtract(7, 'day');
            updateCalendarList(prevStart, prevEnd);
        };

        $scope.editSelectedType = function () {
            $scope.display.processing = undefined;
            $scope.editedResourceType = model.resourceTypes.selection()[0];
            template.close('resources');
            $scope.updateSlotProfileField($scope.editedResourceType.structure);
            template.open('resources', 'resource/edit-resource-type');
        };

        $scope.updateSlotProfileField = function (struct) {
            $scope.slotProfilesComponent.getSlotProfiles(struct.id, function (data) {
                $scope.slotprofiles = [];
                data.forEach(function (slot) {
                    if (slot.slots.length !== 0) {
                        $scope.slotprofiles.push(slot);
                    }
                });
                $scope.safeApply();
            });
        };

        $scope.removeSelectedTypes = function () {
            $scope.display.confirmRemoveTypes = true;
        };

        $scope.doRemoveTypes = function () {
            model.resourceTypes.removeSelectedTypes();
            $scope.display.confirmRemoveTypes = false;
            template.close('resources');
            $scope.closeResourceType();
            $scope.isManage = true;
            $scope.currentResourceType = undefined;
            model.refreshRessourceType();
        };

        // display a warning when editing a resource and changing the resource type (not in creation mode).
        $scope.resourceTypeModified = function () {
            if (
                $scope.currentResourceType != $scope.editedResource.type &&
                !$scope.isCreation
            ) {
                notify.info('rbs.type.info.change');
            }
            // update color of color picker only in case of creation
            if ($scope.editedResource.id === undefined) {
                $scope.editedResource.validation = $scope.editedResource.type.validation;
                $scope.editedResource.color = $scope.editedResource.type.color;
            }
        };

        var updateCalendarList = function (start, end) {
            model.bookings.filters.startMoment.date(start.date());
            model.bookings.filters.startMoment.month(start.month());
            model.bookings.filters.startMoment.year(start.year());

            model.bookings.filters.endMoment.date(end.date());
            model.bookings.filters.endMoment.month(end.month());
            model.bookings.filters.endMoment.year(end.year());

            $scope.bookings.syncForShowList();
            $scope.bookings.applyFilters();
        };

        var updateCalendarSchedule = function (newDate) {
            model.calendar.firstDay.date(newDate.date());
            model.calendar.firstDay.month(newDate.month());
            model.calendar.firstDay.year(newDate.year());
            $scope.bookings.sync();
            CalendarUtil.fixViewNotDisplayed();
            ($('.hiddendatepickerform') as any).datepicker('setValue', newDate.format("DD/MM/YYYY")).datepicker('update');
            ($('.hiddendatepickerform') as any).trigger({type: 'changeDate', date: newDate});
        };

        this.initialize();

        $scope.showDaySelection = true;

        $scope.saveTreeState = function () {
            var state = $scope.structuresWithTypes.map(function (struct) {
                return {
                    id: struct.id,
                    expanded: struct.expanded === true,
                    selected: struct.selected === true,
                    types: struct.types.map(function (type) {
                        return {
                            id: type.id,
                            expanded: type.expanded === true,
                            resources: type.resources.all.map(function (resource) {
                                return {
                                    id: resource.id,
                                    selected: resource.selected === true,
                                };
                            })
                        };
                    })
                };
            });
            state.push(
                {
                    id: $scope.sharedStructure.id,
                    expanded: $scope.sharedStructure.expanded === true,
                    selected: $scope.sharedStructure.selected === true,
                    types: $scope.sharedStructure.types.map(function (type) {
                        return {
                            id: type.id,
                            expanded: type.expanded === true,
                            resources: type.resources.all.map(function (resource) {
                                return {
                                    id: resource.id,
                                    selected: resource.selected === true
                                };
                            })
                        };
                    })
                }
            );
            model.saveTreeState(state);
        };

        $scope.initExportDisplay = function () {
            $scope.exportComponent.display = {
                state: 0,
                STATE_FORMAT: 0,
                STATE_RESOURCES: 1,
                STATE_DATE: 2,
                STATE_VIEW: 3
            };
        };

        $scope.formatDate = function (date) {
            return moment(date).format('DD/MM/YYYY');
        };

        $scope.switchNotification = function (resource, resourceType) {
            //$scope.switchSelect(resource);
            if (resource.notified) {
                $scope.notificationsComponent.removeNotification(resource.id);
                resource.notified = false;
            } else {
                $scope.notificationsComponent.postNotification(resource.id);
                resource.notified = true;
            }
            $scope.checkNotificationsResourceType(resourceType);
        }

        $scope.switchNotifications = function (resourceType) {
            if (
                resourceType.resources.every(function (resource) {
                    return resource.notified;
                })
            ) {
                $scope.disableNotificationsResources(resourceType);
            } else {
                $scope.enableNotificationsResources(resourceType);
            }
        };

        $scope.disableNotificationsResources = function (resourceType) {
            resourceType.resources.forEach(function (resource) {
                resource.notified = false;
            });
            $scope.notificationsComponent.removeNotifications(resourceType.id);
            resourceType.notified = 'none';
        };

        $scope.enableNotificationsResources = function (resourceType) {
            resourceType.resources.forEach(function (resource) {
                resource.notified = true;
            });
            $scope.notificationsComponent.postNotifications(resourceType.id);
            resourceType.notified = 'all';
        };

        $scope.checkNotificationsResourceType = function (resourceType) {
            if (resourceType.resources.all.length === 0) {
                resourceType.notified = 'none';
            } else if (
                resourceType.resources.every(function (resource) {
                    return resource.notified;
                })
            ) {
                resourceType.notified = 'all';
            } else if (
                resourceType.resources.every(function (resource) {
                    return !resource.notified;
                })
            ) {
                resourceType.notified = 'none';
            } else {
                resourceType.notified = 'some';
            }
        };

        $scope.selectBooking = function (currentBooking) {
            if (currentBooking.is_periodic === true) {
                return $scope.selectedBooking._slots;
            } else {
                return [$scope.selectedBooking];
            }
        };

        // Quantity / Availability

        $scope.index = 0;
        $scope.displayLightbox = {
            saveQuantity: false,
            saveAvailability: false,
            deleteAvailability: false
        };
        $scope.bookingsConflictingOneAvailability = [];
        $scope.bookingsOkOneAvailability = [];
        $scope.bookingsConflictingResource = [];
        $scope.bookingsOkResource = [];

        $scope.syncBookingsUsingResource = (resource, booking?) : void => {
            $scope.bookingsConflictingResource = [];
            $scope.bookingsOkResource = [];
            for (let b of resource.bookings.all) {
                if (!b.is_periodic && DateUtils.isNotPast(b.startMoment) && b.quantity) {
                    let timeslotQuantityDispo = AvailabilityUtil.getTimeslotQuantityAvailable(b, resource, null, booking);
                    let isConflicting = timeslotQuantityDispo < 0;

                    if (isConflicting && (b.status === model.STATE_CREATED || b.status === model.STATE_VALIDATED)) {
                        $scope.bookingsConflictingResource.push(b);
                    }
                    else if (!isConflicting && b.status === model.STATE_SUSPENDED) {
                        $scope.bookingsOkResource.push(b);
                    }
                }
            }
        };

        const syncBookingsUsingAvailability = (availability, resource, isForDelete = false) : void => {
            // Checks if some bookings use this (un)availability
            $scope.bookingsConflictingOneAvailability = [];
            $scope.bookingsOkOneAvailability = [];
            for (let booking of resource.bookings.all) {
                if (AvailabilityUtil.isBookingOverlappingAvailability(booking, availability)) {
                    let timeslotQuantityDispo = AvailabilityUtil.getTimeslotQuantityAvailable(booking, resource, availability);
                    let isConflicting = false;
                    availability.quantity = isForDelete ? 0 : availability.quantity;

                    if (availability.is_unavailability) {
                        isConflicting = timeslotQuantityDispo - availability.quantity < 0;
                    }
                    else {
                        isConflicting = timeslotQuantityDispo + availability.quantity < 0;
                    }

                    if (isConflicting && (booking.status === model.STATE_CREATED || booking.status === model.STATE_VALIDATED)) {
                        $scope.bookingsConflictingOneAvailability.push(booking);
                    }
                    else if (!isConflicting && booking.status === model.STATE_SUSPENDED) {
                        $scope.bookingsOkOneAvailability.push(booking);
                    }
                }
            }
        };

        $scope.treatBookings = (resource, bookingsConflicting, bookingsOk) : void => {
            // Suspend conflicting bookings
            if (bookingsConflicting.length > 0) {
                let siblingsBookings = getSiblingsPeriodicBookings(bookingsConflicting);
                bookingsConflicting = bookingsConflicting.concat(siblingsBookings);
                bookingsConflicting = $scope.filterBookingsToProcess(bookingsConflicting);
                if (bookingsConflicting.length > 0) {
                    suspendBookings(bookingsConflicting);
                }
            }

            // Validate or submit all suspended bookings not conflicting anymore
            if (bookingsOk.length > 0) {
                bookingsOk = $scope.filterBookingsToProcess(bookingsOk);
                let finalBookingsOk = bookingsOk.slice();
                let allSiblingsBookings = getSiblingsPeriodicBookings(bookingsOk);

                for (let booking of bookingsOk) {
                    let siblingsBookings = allSiblingsBookings.filter(b => b.parent_booking_id === booking.parent_booking_id);
                    let allSiblingsOk = true;
                    let i = 0;

                    while (i < siblingsBookings.length && allSiblingsOk) {
                        let isSiblingOk = AvailabilityUtil.getTimeslotQuantityAvailable(siblingsBookings[i], resource) >= 0;
                        allSiblingsOk = allSiblingsOk && isSiblingOk;
                        i++;
                    }

                    if (allSiblingsOk) {
                        finalBookingsOk = finalBookingsOk.concat(siblingsBookings);
                    }
                    else {
                        let index = finalBookingsOk.map(b => b.id).indexOf(booking.id);
                        finalBookingsOk.splice(index, 1);
                    }
                }
                if (finalBookingsOk.length > 0) {
                    resource.validation ? submitBookings(finalBookingsOk) : validateBookings(finalBookingsOk);
                }
            }
        };

        const getSiblingsPeriodicBookings = (listBookingsToCheck) : any[] => {
            let parentIds: any = [];
            for (let booking of listBookingsToCheck) {
                if (booking.parent_booking_id && !parentIds.includes(booking.parent_booking_id)) {
                    parentIds.push(booking.parent_booking_id);
                }
            }

            let siblingsBookings = [];
            for (let booking of $scope.bookings.all) {
                if (booking.parent_booking_id && parentIds.includes(booking.parent_booking_id) && !listBookingsToCheck.map(b => b.id).includes(booking.id)) {
                    siblingsBookings.push(booking);
                }
            }

            return siblingsBookings;
        };

        $scope.formatTextTooltipQuantity = (item:any) : string => {
            let booking = $scope.bookings.find(b => b.id === item.id);
            // TODO booking.resource.quantity devient getQuantityByPeriod(resource, start, end)
            return (booking.quantity ? booking.quantity : "?") + " / " + booking.resource.quantity;
        };

        const validateBookings = (bookings) : void => {
            $scope.processBookings = bookings;
            $scope.display.processing = true;
            $scope.currentErrors = [];
            try {
                var actions = $scope.processBookings.length;
                _.each($scope.processBookings, function (booking) {
                    booking.validate(
                        function () {
                            actions--;
                            if (actions === 0) {
                                $scope.display.processing = undefined;
                                $scope.bookings.deselectAll();
                                $scope.closeBooking();
                                model.refreshBookings($scope.display.list);
                            }
                        },
                        function (e) {
                            $scope.currentErrors.push(e);
                            actions--;
                            if (actions === 0) {
                                $scope.display.processing = undefined;
                                $scope.showActionErrors();
                                model.refreshBookings($scope.display.list);
                            }
                        }
                    );
                });
            } catch (e) {
                $scope.display.processing = undefined;
                $scope.currentErrors.push({error: 'rbs.error.technical'});
            }
        };

        const suspendBookings = (bookings) : void => {
            $scope.processBookings = bookings;
            $scope.display.processing = true;
            try {
                var actions = $scope.processBookings.length;
                _.each($scope.processBookings, function (booking) {
                    booking.suspend(
                        function () {
                            actions--;
                            if (actions === 0) {
                                $scope.display.processing = undefined;
                                model.refreshBookings($scope.display.list);
                            }
                        },
                        function (e) {
                            $scope.currentErrors.push(e);
                            actions--;
                            if (actions === 0) {
                                $scope.display.processing = undefined;
                                $scope.showActionErrors();
                                model.refreshBookings($scope.display.list);
                            }
                        }
                    );
                });
            } catch (e) {
                $scope.display.processing = undefined;
                $scope.currentErrors.push({error: 'rbs.error.technical'});
            }
        };

        const submitBookings = (bookings) : void => {
            $scope.processBookings = bookings;
            $scope.display.processing = true;
            try {
                var actions = $scope.processBookings.length;
                _.each($scope.processBookings, function (booking) {
                    booking.submit(
                        function () {
                            actions--;
                            if (actions === 0) {
                                $scope.display.processing = undefined;
                                model.refreshBookings($scope.display.list);
                            }
                        },
                        function (e) {
                            $scope.currentErrors.push(e);
                            actions--;
                            if (actions === 0) {
                                $scope.display.processing = undefined;
                                $scope.showActionErrors();
                                model.refreshBookings($scope.display.list);
                            }
                        }
                    );
                });
            } catch (e) {
                $scope.display.processing = undefined;
                $scope.currentErrors.push({error: 'rbs.error.technical'});
            }
        };

        $scope.initEditedAvailability = () : void => {
            $scope.editedAvailability = new Availability($scope.editedResource);
        };

        $scope.saveAvailability = (availability) : void => {
            $scope.currentErrors = [];

            // Formats dates if they are Date format
            availability.start_date = DateUtils.formatMomentIfDate(availability.start_date);
            availability.end_date = DateUtils.formatMomentIfDate(availability.end_date);

            // Formats times if they are strings
            availability.start_time = DateUtils.formatTimeIfString(availability.start_time);
            availability.end_time = DateUtils.formatTimeIfString(availability.end_time);

            let hasErrors = AvailabilityUtil.isAvailabilityInvalid(availability, $scope.today, $scope.currentErrors);

            if (hasErrors) {
                $scope.safeApply();
                return;
            }

            syncBookingsUsingAvailability(availability, $scope.editedResource);

            if ($scope.bookingsConflictingOneAvailability.length > 0) {
                $scope.displayLightbox.saveAvailability = true;
            }
            else {
                $scope.doSaveAvailability();
            }

            $scope.safeApply();
        };

        $scope.doSaveAvailability = async () : Promise<void> => {
            await availabilityService.save($scope.editedAvailability);
            await $scope.editedResource.syncResourceAvailabilities();
            $scope.initEditedAvailability();
            $scope.display.processing = undefined;
            $scope.displayLightbox.saveAvailability = false;
            $scope.treatBookings($scope.editedResource, $scope.bookingsConflictingOneAvailability, $scope.bookingsOkOneAvailability);
            $scope.safeApply();
        };

        $scope.deleteAvailability = (availability) : void => {
            $scope.editedAvailability = availability;
            $scope.displayLightbox.deleteAvailability = true;
        };

        $scope.confirmDeleteAvailability = (availability) : void => {
            syncBookingsUsingAvailability(availability, $scope.editedResource, true);

            if ($scope.bookingsConflictingOneAvailability.length > 0) {
                $scope.displayLightbox.deleteAvailability = false;
                $scope.displayLightbox.saveAvailability = true;
                $scope.displayLightbox.wasDeleteLightbox = true;
            }
            else {
                $scope.doDeleteAvailability();
            }
        };

        $scope.doDeleteAvailability = async () : Promise<void> => {
            await availabilityService.delete($scope.editedAvailability);
            await $scope.editedResource.syncResourceAvailabilities();
            $scope.initEditedAvailability();
            $scope.display.processing = undefined;
            $scope.displayLightbox.deleteAvailability = false;
            $scope.displayLightbox.saveAvailability = false;
            $scope.displayLightbox.wasDeleteLightbox = false;
            $scope.treatBookings($scope.editedResource, $scope.bookingsConflictingOneAvailability, $scope.bookingsOkOneAvailability);
            $scope.safeApply();
        };

        $scope.editAvailability = (availability) : void => {
            $scope.editedAvailability = availability;
        };

        $scope.closeEditAvailability = async () : Promise<void> => {
            await $scope.editedResource.syncResourceAvailabilities();
            $scope.initEditedAvailability();
            $scope.safeApply();
        }

        $scope.syncAndTreatBookingsUsingResource = async (resource, booking?) : Promise<void> => {
            // Deals with bookings validation system
            await $scope.syncBookingsUsingResource(resource, booking);
            await $scope.treatBookings(resource, $scope.bookingsConflictingResource, $scope.bookingsOkResource);
        };

        $scope.filterBookingsToProcess = (bookings) : void => {
            return bookings.filter(b => b.owner_id  === model.me.userId || b.myRights.proccess || model.me.functions.ADMIN_LOCAL);
        };

        // Utils

        $scope.displayTime = (date) => {
            return DateUtils.displayTime(date);
        };

        $scope.displayDate = (date) => {
            return DateUtils.displayDate(date);
        };

        $scope.getRightList = (resource) : Availability[] => {
            return AvailabilityUtil.getRightList(resource);
        };
    }]);