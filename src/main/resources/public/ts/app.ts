import {ng, routes} from 'entcore';
import {RbsController} from './controllers/controller';
import {timePicker} from './directives/additional';

ng.controllers.push(RbsController);
ng.directives.push(timePicker);

routes.define(function ($routeProvider) {
    $routeProvider
        .when('/booking/:bookingId', {
            action: 'viewBooking',
        })
        .when('/booking/:bookingId/:start', {
            action: 'viewBooking',
        });
});
