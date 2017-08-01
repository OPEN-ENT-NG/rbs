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

import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.notEmptyResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

import java.util.ArrayList;
import java.util.List;

import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.http.Renders;
import net.atos.entng.rbs.service.ResourceTypeService;
import net.atos.entng.rbs.service.ResourceTypeServiceSqlImpl;
import net.atos.entng.rbs.service.UserService;
import net.atos.entng.rbs.service.UserServiceDirectoryImpl;

import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.mongodb.MongoDbResult;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
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
import fr.wseduc.webutils.request.RequestUtils;

public class ResourceTypeController extends ControllerHelper {

	private static final String DIRECTORY_ADDRESS = "directory";
	private static final I18n i18n = I18n.getInstance();
	private final ResourceTypeService resourceTypeService;
	private final UserService userService;

	public ResourceTypeController(EventBus eb) {
		resourceTypeService = new ResourceTypeServiceSqlImpl();
		userService = new UserServiceDirectoryImpl(eb);
	}
	// TODO : refactor ResourceTypeController to use resourceService instead of crudService

	@Get("/types")
	@ApiDoc("List resource types")
	@SecuredAction("rbs.type.list")
	public void listResourceTypes(final HttpServerRequest request){
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				final List<String> groupsAndUserIds = new ArrayList<>();
				groupsAndUserIds.add(user.getUserId());
				if (user.getGroupsIds() != null) {
					groupsAndUserIds.addAll(user.getGroupsIds());
				}

				resourceTypeService.list(groupsAndUserIds, user, arrayResponseHandler(request));
			}
		});
	}

	@Get("/type/:id")
	@ApiDoc("Get resource type")
	@SecuredAction(value = "rbs.read", type = ActionType.RESOURCE)
	public void getResourceType(final HttpServerRequest request){
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				String id = request.params().get("id");
				crudService.retrieve(id, notEmptyResponseHandler(request));
			}
		});
	}

	@Post("/type")
	@ApiDoc("Create resource type")
	@SecuredAction("rbs.type.create")
	public void createResourceType(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, pathPrefix + "createResourceType",  new Handler<JsonObject>() {
						@Override
						public void handle(JsonObject resourceType) {
							String slotprofile = resourceType.getString("slotprofile");
							if (slotprofile == null || slotprofile.isEmpty()) {
								resourceType.putString("slotprofile", null);
							}
							crudService.create(resourceType, user, notEmptyResponseHandler(request));
						}
					});
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	@Put("/type/:id")
	@ApiDoc("Update resource type")
	@SecuredAction(value = "rbs.manager", type = ActionType.RESOURCE)
	public void updateResourceType(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, pathPrefix + "updateResourceType", new Handler<JsonObject>() {
						@Override
						public void handle(final JsonObject resourceType) {
							final String id = request.params().get("id");
							String slotprofile = resourceType.getString("slotprofile");
							if (slotprofile == null || slotprofile.isEmpty()) {
								resourceType.putString("slotprofile", null);
							}
							crudService.update(id, resourceType, user, new Handler<Either<String, JsonObject>>() {
								@Override
								public void handle(Either<String, JsonObject> event) {
									if (event.isRight()) {
										if(resourceType == null || resourceType.size() == 0) {
											renderJson(request, event.right().getValue());
										}
										else {
											resourceTypeService.overrideValidationChild(id,resourceType.getBoolean("validation"),new Handler<Either<String, JsonObject>>(){
												@Override
												public void handle(Either<String, JsonObject> event) {
													Boolean shouldOverride = resourceType.getBoolean("extendcolor");
													if (shouldOverride != null && shouldOverride) {
														resourceTypeService.overrideColorChild(id,resourceType.getString("color"),defaultResponseHandler(request));
													}
													else {
														log.trace("Update resource type " + id + " without overriding color for child");
														renderJson(request, event.right().getValue());
													}
												}
											});
										}

									} else {
										JsonObject error = new JsonObject()
												.putString("error", event.left().getValue());
										renderJson(request, error, 400);
									}
								}
							});
						}
					});
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	@Delete("/type/:id")
	@ApiDoc("Delete resource type")
	@SecuredAction(value = "rbs.manager", type = ActionType.RESOURCE)
	public void deleteResourceType(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					String id = request.params().get("id");
					crudService.delete(id, user, defaultResponseHandler(request, 204));
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	@Get("/type/:id/moderators")
	@ApiDoc("Return moderators. This webservice is used to display moderators' names")
	@SecuredAction(value = "rbs.read", type = ActionType.RESOURCE)
	public void getModerators(final HttpServerRequest request) {
		String typeId = request.params().get("id");
		resourceTypeService.getModeratorsIds(typeId, new Handler<Either<String, JsonArray>>() {
			@Override
			public void handle(Either<String, JsonArray> event) {
				if (event.isRight()) {
					JsonArray result = event.right().getValue();
					if(result == null || result.size() == 0) {
						renderJson(request, event.right().getValue());
					}
					else {
						JsonArray userIds = new JsonArray();
						JsonArray groupIds = new JsonArray();

						for (Object m : result) {
							if(!(m instanceof JsonObject)) continue;
							JsonObject member = (JsonObject) m;
							String uId = member.getString("user_id", null);
							String gId = member.getString("group_id", null);
							if(uId != null) {
								userIds.addString(uId);
							}
							else if(gId != null) {
								groupIds.addString(gId);
							}
						}

						if(userIds.size() == 0 && groupIds.size() == 0) {
							renderJson(request, event.right().getValue());
						}
						else {
							userService.getUsers(userIds, groupIds, arrayResponseHandler(request));
						}
					}

				} else {
					JsonObject error = new JsonObject()
							.putString("error", event.left().getValue());
					renderJson(request, error, 400);
				}
			}
		});
	}

	@Override
	@Get("/share/json/:id")
	@ApiDoc("List rights for a given resource type")
	@SecuredAction(value = "rbs.manager", type = ActionType.RESOURCE)
	public void shareJson(final HttpServerRequest request){
		// TODO Improve : temporary unique share url to match Front-end ShareController urls
		super.shareJson(request, false);
	}

	@Put("/share/json/:id")
	@ApiDoc("Add rights for a given resource type")
	@SecuredAction(value = "rbs.manager", type = ActionType.RESOURCE)
	public void shareJsonSubmit(final HttpServerRequest request){
		// TODO Improve : temporary unique share url to match Front-end ShareController urls
		super.shareJsonSubmit(request, null, false);
	}

	@Override
	@Put("/share/remove/:id")
	@ApiDoc("Remove rights for a given resource type")
	@SecuredAction(value = "rbs.manager", type = ActionType.RESOURCE)
	public void removeShare(final HttpServerRequest request){
		// TODO Improve : temporary unique share url to match Front-end ShareController urls
		super.removeShare(request, false);
	}

	@ApiDoc("Get all slot profiles for a school")
	@Get("/slotprofiles/schools/:schoolId")
	public void listSlotProfilesBySchool(HttpServerRequest request) {
		final String structureId = request.params().get("schoolId");
		if (structureId == null) {
			String errorMessage = i18n.translate(
					"directory.slot.bad.request.invalid.structure",
					Renders.getHost(request),
					I18n.acceptLanguage(request));
			badRequest(request, errorMessage);
			return;
		}
		JsonObject action = new JsonObject()
				.putString("action", "list-slotprofiles")
				.putString("structureId", structureId);
		Handler<Either<String, JsonArray>> handler = arrayResponseHandler(request);
		eb.send(DIRECTORY_ADDRESS, action, validResultHandler(handler));
	}

	@ApiDoc("Get all slots for a slot profile")
	@Get("/slotprofiles/:idSlotProfile/slots")
	public void listSlotsInAProfile(HttpServerRequest request) {
		String idSlotProfile = request.params().get("idSlotProfile");
		JsonObject action = new JsonObject()
				.putString("action", "list-slots")
				.putString("slotProfileId", idSlotProfile);

		Handler<Either<String, JsonObject>> handler = notEmptyResponseHandler(request);
		eb.send(DIRECTORY_ADDRESS, action, MongoDbResult.validResultHandler(handler));
	}

	@Post("/type/notification/add/:id")
	@ApiDoc("Add notifications")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void addNotifications (final HttpServerRequest request){
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					String id = request.params().get("id");
					resourceTypeService.addNotifications (id, user, defaultResponseHandler(request));
				} else {
					log.debug("User not found in session.");
					Renders.unauthorized(request);
				}
			}
		});
	}

	@Delete("/type/notification/remove/:id")
	@ApiDoc("Remove notifications")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void removeNotifications (final HttpServerRequest request){
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					String id = request.params().get("id");
					resourceTypeService.removeNotifications (id, user, defaultResponseHandler(request));
				} else {
					log.debug("User not found in session.");
					Renders.unauthorized(request);
				}
			}
		});
	}
}
