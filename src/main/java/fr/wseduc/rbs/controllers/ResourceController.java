package fr.wseduc.rbs.controllers;

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.notEmptyResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.service.VisibilityFilter;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.rbs.filters.TypeAndResourceAppendPolicy;
import fr.wseduc.rbs.service.ResourceService;
import fr.wseduc.rbs.service.ResourceServiceSqlImpl;
import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.ResourceFilter;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;

public class ResourceController extends ControllerHelper {

	private static final String SCHEMA_RESOURCE_CREATE = "createResource";
	private static final String SCHEMA_RESOURCE_UPDATE = "updateResource";

	private final ResourceService resourceService;

	public ResourceController() {
		resourceService = new ResourceServiceSqlImpl();
	}
	// TODO : refactor ResourceController to use resourceService instead of crudService

	@Override
	@Get("/resources")
	@ApiDoc("List all resources visible by current user")
	@SecuredAction("rbs.resource.list")
	public void list(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				crudService.list(VisibilityFilter.OWNER_AND_SHARED, user, arrayResponseHandler(request));
			}
		});
	}

	@Get("/resource/:id")
	@ApiDoc("Get resource")
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	@SecuredAction(value = "rbs.read", type = ActionType.RESOURCE)
	public void get(final HttpServerRequest request) {
		super.retrieve(request);
	}

	@Override
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

	@Override
	@Put("/resource/:id")
	@ApiDoc("Update resource")
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	@SecuredAction(value = "rbs.publish", type = ActionType.RESOURCE)
	public void update(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, pathPrefix + SCHEMA_RESOURCE_UPDATE, new Handler<JsonObject>() {
						@Override
						public void handle(JsonObject object) {
							String id = request.params().get("id");
							resourceService.updateResource(id, object, defaultResponseHandler(request));

							// TODO : si was_available != is_available, notifier les demandeurs
						}
					});
				} else {
					log.debug("User not found in session.");
					Renders.unauthorized(request);
				}
			}
		});
	}

	@Override
	@Delete("/resource/:id")
	@ApiDoc("Delete resource")
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	@SecuredAction(value = "rbs.manager", type = ActionType.RESOURCE)
	public void delete(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					String id = request.params().get("id");
					crudService.delete(id, user, defaultResponseHandler(request));
				} else {
					log.debug("User not found in session.");
					Renders.unauthorized(request);
				}
			}
		});
	}

	@Get("/resource/share/json/:id")
	@ApiDoc("List rights for a given resource")
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	@SecuredAction(value = "rbs.manager", type = ActionType.RESOURCE)
	public void share(final HttpServerRequest request) {
		super.shareJson(request, false);
	}

	@Put("/resource/share/json/:id")
	@ApiDoc("Add rights for a given resource")
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	@SecuredAction(value = "rbs.manager", type = ActionType.RESOURCE)
	public void shareSubmit(final HttpServerRequest request) {
		super.shareJsonSubmit(request, null, false);
	}

	@Put("/resource/share/remove/:id")
	@ApiDoc("Remove rights for a given resource")
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	@SecuredAction(value = "rbs.manager", type = ActionType.RESOURCE)
	public void shareRemove(final HttpServerRequest request) {
		super.removeShare(request, false);
	}
}
