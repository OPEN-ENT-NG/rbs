package fr.wseduc.rbs.controllers;

import static org.entcore.common.http.response.DefaultResponseHandler.notEmptyResponseHandler;

import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;

public class ResourceController extends ControllerHelper {

	private static final String SCHEMA_RESOURCE_CREATE = "resource.create";
	
	@Get("/resources")
	@ApiDoc("List all resources visible by current user")
	@SecuredAction("rbs.resource.list")
	public void list(HttpServerRequest request) {
		super.list(request);
	}

	/*
	@Get("/resources/:typeId/booking")
	@ApiDoc("List resources for a given typeId, with their associated bookings for a given duration")
	@SecuredAction(value = "rbs.read", type = ActionType.RESOURCE)
	public void listBookings(HttpServerRequest request) {
		
	}
	*/

	@Get("/resource/:id")
	@ApiDoc("Get resource")
	@SecuredAction(value = "rbs.read", type = ActionType.RESOURCE)
	public void get(HttpServerRequest request) {
		super.retrieve(request);
	}
	
	/*
	@Get("/resource/:id/booking")
	@ApiDoc("Get a resource with its associated bookings for a given duration")
	@SecuredAction(value = "rbs.read", type = ActionType.RESOURCE)
	public void getBookings(HttpServerRequest request) {
		
	}
	*/
	
	@Post("/resources")
	@ApiDoc("Create resource")
	@SecuredAction("rbs.resource.create")
	public void create(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, pathPrefix + SCHEMA_RESOURCE_CREATE, new Handler<JsonObject>() {
						@Override
						public void handle(JsonObject object) {
							crudService.create(object, user, notEmptyResponseHandler(request));
						}
					});
				} else {
					log.debug("User not found in session.");
					Renders.unauthorized(request);
				}
			}
		});
	}

	@Put("/resource/:id")
	@ApiDoc("Update resource")
	@SecuredAction(value = "rbs.publish", type = ActionType.RESOURCE)
	public void update(HttpServerRequest request) {
		super.update(request);
	}

	@Delete("/resource/:id")
	@ApiDoc("Delete resource")
	@SecuredAction(value = "rbs.manager", type = ActionType.RESOURCE)
	public void delete(HttpServerRequest request) {
		super.delete(request);
	}

	@Get("/resource/share/json/:id")
	@ApiDoc("List rights for a given resource")
	@SecuredAction(value = "rbs.manager", type = ActionType.RESOURCE)
	public void share(HttpServerRequest request) {
		super.shareJson(request, false);
	}

	@Put("/resource/share/json/:id")
	@ApiDoc("Add rights for a given resource")
	@SecuredAction(value = "rbs.manager", type = ActionType.RESOURCE)
	public void shareSubmit(HttpServerRequest request) {
		super.shareJsonSubmit(request, null, false);
	}

	@Put("/resource/share/remove/:id")
	@ApiDoc("Remove rights for a given resource")
	@SecuredAction(value = "rbs.manager", type = ActionType.RESOURCE)
	public void shareRemove(HttpServerRequest request) {
		super.removeShare(request, false);
	}
}
