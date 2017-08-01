/*
 * Copyright © Région Nord Pas de Calais-Picardie,  Département 91, Région Aquitaine-Limousin-Poitou-Charentes, 2016.
 *
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package net.atos.entng.rbs.controllers;

import static net.atos.entng.rbs.Rbs.RBS_NAME;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.notEmptyResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.atos.entng.rbs.filters.TypeAndResourceAppendPolicy;
import net.atos.entng.rbs.filters.TypeOwnerSharedOrLocalAdmin;
import net.atos.entng.rbs.service.ResourceService;
import net.atos.entng.rbs.service.ResourceServiceSqlImpl;

import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;

public class ResourceController extends ControllerHelper {

	private static final String RESOURCE_AVAILABLE_EVENT_TYPE = RBS_NAME + "_RESOURCE_AVAILABLE";
	private static final String RESOURCE_UNAVAILABLE_EVENT_TYPE = RBS_NAME + "_RESOURCE_UNAVAILABLE";

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
				if (user != null) {
					final List<String> groupsAndUserIds = new ArrayList<>();
					groupsAndUserIds.add(user.getUserId());
					if (user.getGroupsIds() != null) {
						groupsAndUserIds.addAll(user.getGroupsIds());
					}

					resourceService.listResources(groupsAndUserIds, user, arrayResponseHandler(request));
				}
				else {
					log.debug("User not found in session.");
					unauthorized(request);
				}

			}
		});
	}

	@Get("/resource/:id")
	@ApiDoc("Get resource")
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	@SecuredAction(value = "rbs.read", type = ActionType.RESOURCE)
	public void get(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				String id = request.params().get("id");
				crudService.retrieve(id, notEmptyResponseHandler(request));
			}
		});
	}

	@Override
	@Post("/type/:id/resource") // Parameter "id" is the resourceTypeId
	@ApiDoc("Create resource")
	@ResourceFilter(TypeOwnerSharedOrLocalAdmin.class)
	@SecuredAction(value = "rbs.manager", type = ActionType.RESOURCE)
	public void create(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, pathPrefix + SCHEMA_RESOURCE_CREATE, new Handler<JsonObject>() {
						@Override
						public void handle(JsonObject resource) {
							long minDelay = resource.getLong("min_delay", -1L);
							long maxDelay = resource.getLong("max_delay", -1L);
							if(minDelay > -1L && maxDelay > -1L && minDelay >= maxDelay) {
								badRequest(request, "rbs.resource.bad.request.min_delay.greater.than.max_delay");
							}
							String resourceTypeId = request.params().get("id");
							resource.putString("type_id", resourceTypeId);

							resourceService.createResource(resource, user, notEmptyResponseHandler(request));
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
						public void handle(JsonObject resource) {
							String id = request.params().get("id");
							final boolean isAvailable = resource.getBoolean("is_available");
							final boolean wasAvailable = resource.getBoolean("was_available");

							long minDelay = resource.getLong("min_delay", -1L);
							long maxDelay = resource.getLong("max_delay", -1L);
							if(minDelay > -1L && maxDelay > -1L && minDelay >= maxDelay) {
								badRequest(request, "rbs.resource.bad.request.min_delay.greater.than.max_delay");
							}

							Handler<Either<String, JsonObject>> handler = new Handler<Either<String, JsonObject>>() {
								@Override
								public void handle(Either<String, JsonObject> event) {
									if (event.isRight()) {
										Renders.renderJson(request, event.right().getValue(), 200);
										notifyResourceAvailability(request, user, event.right().getValue(),
												isAvailable, wasAvailable);
									} else {
										JsonObject error = new JsonObject()
												.putString("error", event.left().getValue());
										Renders.renderJson(request, error, 400);
									}
								}
							};

							resourceService.updateResource(id, resource, handler);
						}
					});
				} else {
					log.debug("User not found in session.");
					Renders.unauthorized(request);
				}
			}
		});
	}

	/**
	 * Notify booking owners that a resource is now (un)available
	 */
	private void notifyResourceAvailability(final HttpServerRequest request, final UserInfos user,
			final JsonObject message, final boolean isAvailable, final boolean wasAvailable){

		// Notify only if the availability has been changed
		if(wasAvailable != isAvailable) {
			final long resourceId = message.getLong("id", 0L);
			final String resourceName = message.getString("name", null);

			final String eventType;
			final String notificationName;
			if (isAvailable) {
				eventType = RESOURCE_AVAILABLE_EVENT_TYPE;
				notificationName = "resource-available";
			}
			else {
				eventType = RESOURCE_UNAVAILABLE_EVENT_TYPE;
				notificationName = "resource-unavailable";
			}

			if (resourceId == 0L || resourceName == null) {
				log.error("Could not get resourceId or resourceName from response. Unable to send timeline "+ eventType + " notification.");
				return;
			}

			resourceService.getBookingOwnersIds(resourceId, new Handler<Either<String, JsonArray>>() {
				@Override
				public void handle(Either<String, JsonArray> event) {
					if (event.isRight()) {
						Set<String> recipientSet = new HashSet<>();
						for(Object o : event.right().getValue()){
							if(!(o instanceof JsonObject)){
								continue;
							}
							JsonObject jo = (JsonObject) o;
							recipientSet.add(jo.getString("owner"));
						}

						if(!recipientSet.isEmpty()) {
							List<String> recipients = new ArrayList<>(recipientSet);

							JsonObject params = new JsonObject();
							params.putString("resource_name", resourceName);

							notification.notifyTimeline(request, "rbs." + notificationName, user, recipients, String.valueOf(resourceId), params);
						}

					} else {
						log.error("Error when calling service getBookingOwnersIds. Unable to send timeline "
								+ eventType + " notification.");
					}
				}
			});

			resourceService.getUserNotification(resourceId, user, new Handler<Either<String, JsonArray>>() {
				@Override
				public void handle(Either<String, JsonArray> event) {
					if (event.isRight()) {
						Set<String> recipientSet = new HashSet<>();
						for(Object o : event.right().getValue()){
							if(!(o instanceof JsonObject)){
								continue;
							}
							JsonObject jo = (JsonObject) o;
							recipientSet.add(jo.getString("user_id"));
						}

						if(!recipientSet.isEmpty()) {
							List<String> recipients = new ArrayList<>(recipientSet);

							JsonObject params = new JsonObject();
							params.putString("resource_name", resourceName);

							notification.notifyTimeline(request, "rbs." + notificationName, user, recipients, String.valueOf(resourceId), params);
						}

					} else {
						log.error("Error when calling service getUserNotification. Unable to send timeline "
								+ eventType + " notification.");
					}
				}
			});
		}
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

	@Post("/resource/notification/add/:id")
	@ApiDoc("Add notification")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void addNotification (final HttpServerRequest request){
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					String id = request.params().get("id");
					resourceService.addNotification (id, user, defaultResponseHandler(request));
				} else {
					log.debug("User not found in session.");
					Renders.unauthorized(request);
				}
			}
		});
	}

	@Delete("/resource/notification/remove/:id")
	@ApiDoc("Remove notification")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void removeNotification (final HttpServerRequest request){
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					String id = request.params().get("id");
					resourceService.removeNotification (id, user, defaultResponseHandler(request));
				} else {
					log.debug("User not found in session.");
					Renders.unauthorized(request);
				}
			}
		});
	}

	@Get("/resource/notifications")
	@ApiDoc("Get notification")
	public void getNotifications (final HttpServerRequest request){
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					resourceService.getNotifications (user, arrayResponseHandler(request));
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
