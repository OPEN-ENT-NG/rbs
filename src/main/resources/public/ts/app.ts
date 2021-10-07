import {ng} from 'entcore';
import {RbsController} from './controllers/controller';
import {timePicker} from './directives/additional';

ng.controllers.push(RbsController);
ng.directives.push(timePicker);