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

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.defaultResponseHandler;

import fr.wseduc.rs.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import net.atos.entng.rbs.models.Availability;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.http.filter.Trace;
import org.entcore.common.user.UserUtils;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.request.RequestUtils;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import net.atos.entng.rbs.filters.TypeAndResourceAppendPolicy;
import net.atos.entng.rbs.service.*;

import java.util.ArrayList;
import java.util.List;

public class AvailabilityController extends ControllerHelper {
	private static final Logger log = LoggerFactory.getLogger(AvailabilityController.class);
	private final AvailabilityService availabilityService;
	private final ResourceService resourceService;

	public AvailabilityController() {
		availabilityService = new AvailabilityServiceSqlImpl();
		resourceService = new ResourceServiceSqlImpl();
	}

	@Get("/availability") // Parameter "id" is the resourceId
	@ApiDoc("List all the availabilities")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void listAvailabilities(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, user -> {
			if (user == null) {
				log.debug("User not found in session.");
				unauthorized(request);
				return;
			}

			final List<String> groupsAndUserIds = new ArrayList<>();
			groupsAndUserIds.add(user.getUserId());
			if (user.getGroupsIds() != null) {
				groupsAndUserIds.addAll(user.getGroupsIds());
			}

			resourceService.listResources(groupsAndUserIds, user, listResource -> {
				if (listResource.isLeft()) {
					log.error("[RBS@listAvailabilities] Fail to list resources for user " + user.getUserId() + " : " + listResource.left().getValue());
					badRequest(request, listResource.left().getValue());
					return;
				}

				JsonArray resources = listResource.right().getValue();
				JsonArray resourceIds = new JsonArray();
				for (int i = 0; i < resources.size(); i++) {
					resourceIds.add(resources.getJsonObject(i).getInteger("id"));
				}

				availabilityService.listAvailabilities(resourceIds, user, arrayResponseHandler(request));
			});
		});
	}

	@Get("/resource/:id/availability") // Parameter "id" is the resourceId
	@ApiDoc("List all the availability of a resource")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void listResourceAvailability(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, user -> {
			if (user == null) {
				log.debug("User not found in session.");
				unauthorized(request);
				return;
			}

			Integer resourceId = Integer.parseInt(request.params().get("id"));
			Boolean is_unavailability = Boolean.parseBoolean(request.params().get("is_unavailability"));
			availabilityService.listResourceAvailability(resourceId, is_unavailability, user, arrayResponseHandler(request));
		});
	}

	@Post("/resource/:id/availability") // Parameter "id" is the resourceId
	@ApiDoc("Create an availability for a given resource")
	@SecuredAction(value = "rbs.publish", type = ActionType.RESOURCE)
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	@Trace(value="CREATE_AVAILABILITY")
	public void createAvailability(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, user -> {
			if (user == null) {
				log.debug("User not found in session.");
				unauthorized(request);
				return;
			}

			RequestUtils.bodyToJson(request, pathPrefix + "createAvailability", json -> {
				Availability availability = treatAvailability(request, json);
				if (availability == null) { return; }

				availabilityService.createAvailability(availability, user, defaultResponseHandler(request));
			});
		});
	}

	@Put("/resource/:id/availability/:availabilityId") // Parameter "id" is the resourceId
	@ApiDoc("Update an availability")
	@SecuredAction(value = "rbs.publish", type = ActionType.RESOURCE)
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	@Trace(value="UPDATE_AVAILABILITY")
	public void updateAvailability(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, user -> {
			if (user == null) {
				log.debug("User not found in session.");
				unauthorized(request);
				return;
			}

			RequestUtils.bodyToJson(request, pathPrefix + "updateAvailability", json -> {
				Availability availability = treatAvailability(request, json);
				if (availability == null) { return; }

				availabilityService.updateAvailability(availability, user, defaultResponseHandler(request));
			});
		});
	}

	@Delete("/resource/:id/availability/:availabilityId")
	@ApiDoc("Delete an availability")
	@SecuredAction(value = "rbs.publish", type = ActionType.RESOURCE)
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	@Trace(value="DELETE_AVAILABILITY")
	public void deleteAvailability(final HttpServerRequest request) {
		Integer availabilityId = Integer.parseInt(request.params().get("availabilityId"));
		availabilityService.deleteAvailability(availabilityId, defaultResponseHandler(request));
	}

	@Delete("/resource/:id/availability/all/:areUnavailability")
	@ApiDoc("Delete all (un)availabilities of a resource")
	@SecuredAction(value = "rbs.publish", type = ActionType.RESOURCE)
	@ResourceFilter(TypeAndResourceAppendPolicy.class)
	@Trace(value="DELETE_ALL_AVAILABILITY")
	public void deleteAllAvailability(final HttpServerRequest request) {
		Integer resourceId = Integer.parseInt(request.params().get("id"));
		String areUnavailability = request.params().get("areUnavailability");
		availabilityService.deleteAllAvailability(resourceId, areUnavailability, arrayResponseHandler(request));
	}

	private Availability treatAvailability(HttpServerRequest request, JsonObject json) {
		Integer resourceId = Integer.parseInt(request.params().get("id"));
		json.put("resourceId", resourceId);

		Integer availabilityId = request.params().contains("availabilityId") ? Integer.parseInt(request.params().get("availabilityId")) : -1;
		Availability availability = new Availability(json, availabilityId);

		// Store boolean array (selected days) as a bit string
		try {
			availability.computeSelectedDaysAsBitString();
		} catch (Exception e) {
			log.error("[RBS@treatAvailability] Error during processing of array 'days'", e);
			renderError(request);
			return null;
		}

		return availability;
	}
}
