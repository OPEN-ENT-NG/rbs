import {ng, routes} from 'entcore';
import {RbsController} from './controllers/controller';
import * as directives from './directives';
import * as services from './services';

for (let directive in directives) {
    ng.directives.push(directives[directive]);
}

for (let service in services) {
    ng.services.push(services[service]);
}

ng.controllers.push(RbsController);


routes.define(function ($routeProvider) {
    $routeProvider
        .when('/booking/:bookingId', {
            action: 'viewBooking',
        })
        .when('/booking/:bookingId/:start', {
            action: 'viewBooking',
        });
});
