import {_, Behaviours, model} from 'entcore';
import {calendarRbsBooking} from "./sniplets/calendar-rbs-booking.sniplet";
import {RBS_CALENDAR_EVENTER} from "./core/enum/rbs-calendar-eventer.enum";
import {Resource} from "./models/resource.model";
import {Booking} from "./models/booking.model";

console.log('RBS behaviours loaded');

var rbsBehaviours = {
    resources: {
        contrib: {
            right: 'net-atos-entng-rbs-controllers-BookingController|createBooking'
        },
        process: {
            right: 'net-atos-entng-rbs-controllers-BookingController|processBooking'
        },
        manage: {
            right: 'net-atos-entng-rbs-controllers-ResourceTypeController|updateResourceType'
        },
        share: {
            right: 'net-atos-entng-rbs-controllers-ResourceTypeController|shareJson'
        }
    },
    workflow: {
        typemanage: 'net.atos.entng.rbs.controllers.ResourceTypeController|createResourceType',
        validator: 'net.atos.entng.rbs.controllers.BookingController|listUnprocessedBookings',
    }
};

Behaviours.register('rbs', {
    behaviours: rbsBehaviours,
    eventerRbs: RBS_CALENDAR_EVENTER,
    sniplets: {
        'calendar-rbs-booking': calendarRbsBooking
    },
    resourceRights: function (resource) {
        var rightsContainer = resource;
        if (resource instanceof Resource && resource.type) {
            rightsContainer = resource.type;
        }
        if ((resource && (resource instanceof Booking || resource.isBookingInstance)) && resource.resource && resource.resource.type) {
            rightsContainer = resource.resource.type;
        }

        if (!resource.myRights) {
            resource.myRights = {};
        }

        // ADML permission check
        var isAdmlForResource = model.me.functions.ADMIN_LOCAL && _.find(model.me.functions.ADMIN_LOCAL.scope, function (structure_id) {
            return structure_id === rightsContainer.school_id;
        });

        for (var behaviour in rbsBehaviours.resources) {
            if (model.me.userId === resource.owner || model.me.userId === rightsContainer.owner || isAdmlForResource || model.me.hasRight(rightsContainer, rbsBehaviours.resources[behaviour])) {
                if (resource.myRights[behaviour] !== undefined) {
                    resource.myRights[behaviour] = resource.myRights[behaviour] && rbsBehaviours.resources[behaviour];
                } else {
                    resource.myRights[behaviour] = rbsBehaviours.resources[behaviour];
                }
            }
        }
        return resource;
    },
    workflow: function () {
        var workflow = {};
        var rbsWorkflow = rbsBehaviours.workflow;
        for (var prop in rbsWorkflow) {
            if (model.me.hasWorkflow(rbsWorkflow[prop])) {
                workflow[prop] = true;
            }
        }
        return workflow;
    }
});