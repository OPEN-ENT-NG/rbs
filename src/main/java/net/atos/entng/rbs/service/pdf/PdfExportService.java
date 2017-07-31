package net.atos.entng.rbs.service.pdf;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import net.atos.entng.rbs.model.ExportRequest;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.core.shareddata.ConcurrentSharedMap;
import org.vertx.java.platform.Verticle;

import java.io.FileOutputStream;
import java.io.StringWriter;
import java.util.Map;

import static net.atos.entng.rbs.model.ExportResponse.getView;

public class PdfExportService extends Verticle implements Handler<Message<JsonObject>> {
	private static final Logger LOG = LoggerFactory.getLogger(PdfExportService.class);

	public static final String PDF_HANDLER_ADDRESS = "rbs.pdf.handler";
	/**
	 * Actions handled by worker
	 */
	public static final String ACTION_CONVERT = "convert";


	private static final String WEEK_HTML_TEMPLATE = "pdftemplate/booking_week.pdf.xhtml";
	private static final String DAY_HTML_TEMPLATE = "pdftemplate/booking_day.pdf.xhtml";
	private static final String LIST_HTML_TEMPLATE = "pdftemplate/booking_list.pdf.xhtml";


	@Override
	public void start() {
		super.start();
		vertx.eventBus().registerHandler(PDF_HANDLER_ADDRESS, this);
	}

	@Override
	public void handle(Message<JsonObject> message) {
		String action = message.body().getString("action", "");
		JsonObject exportResponse = message.body().getObject("data", new JsonObject());
		String scheme = message.body().getString("scheme", "");
		String host = message.body().getString("host", "");
		switch (action) {
			case ACTION_CONVERT:
				generatePdfFile(exportResponse, scheme, host, message);
				break;
			default:
				JsonObject results = new JsonObject();
				results.putString("message", "Unknown action");
				results.putNumber("status", 400);
				message.reply(results);
		}
	}

	private void generatePdfFile(final JsonObject exportResponse, final String scheme, final String host, final Message<JsonObject> message) {
		final String htmlTemplateFile = getTemplate(exportResponse);

		vertx.fileSystem().readFile(htmlTemplateFile, new Handler<AsyncResult<Buffer>>() {
			@Override
			public void handle(AsyncResult<Buffer> result) {
				if (!result.succeeded()) {
					Throwable cause = result.cause();
					LOG.error("Template " + htmlTemplateFile + "  could not be read", cause);
					JsonObject results = new JsonObject();
					results.putNumber("status", 500);
					results.putString("message", cause != null ? cause.getMessage() : "");
					message.reply(results);
					return;
				}

				try {
					JsonObject preparedData = prepareData(exportResponse);

					String filledTemplate = fillTemplate(result.result().toString("UTF-8"), preparedData);

					ConcurrentSharedMap<Object, Object> skins = vertx.sharedData().getMap("skins");
					final String baseUrl = scheme + "://" + host + "/assets/themes/" + skins.get(host) + "/img/";
					JsonObject actionObject = new JsonObject();
					actionObject
							.putBinary("content", filledTemplate.getBytes())
							.putString("baseUrl", baseUrl);
					String node = (String) vertx.sharedData().getMap("server").get("node");
					if (node == null) {
						node = "";
					}
					vertx.eventBus().send(node + "entcore.pdf.generator", actionObject, new Handler<Message<JsonObject>>() {
						public void handle(Message<JsonObject> reply) {
							JsonObject pdfResponse = reply.body();
							if (!"ok".equals(pdfResponse.getString("status"))) {
								String pdfResponseString = pdfResponse.getString("message");
								LOG.error("Conversion error: " + pdfResponseString);
								JsonObject results = new JsonObject();
								results.putNumber("status", 500);
								results.putString("message", pdfResponseString);
								message.reply(results);
								return;
							}

							byte[] pdf = pdfResponse.getBinary("content");

							JsonObject results = new JsonObject();
							results.putNumber("status", 200);
							results.putBinary("content", pdf);
							message.reply(results);
						}
					});

				} catch (Exception e) {
					LOG.error("Conversion Error", e);
					JsonObject results = new JsonObject();
					results.putNumber("status", 500);
					results.putString("message", e.getMessage());
					message.reply(results);
					return;
				}
			}


		});
	}

	private JsonObject prepareData(JsonObject jsonExportResponse) {
		JsonFormatter formatter = JsonFormatter.buildFormater(jsonExportResponse);

		JsonObject convertedJson = formatter.format();
		JsonArray jsonFileArray = new JsonArray();
		jsonFileArray.addObject(convertedJson);

		return new JsonObject().putElement("export", jsonFileArray);
	}

	private String fillTemplate(String templateAsString, JsonObject preparedData) {
		Mustache.Compiler compiler = Mustache.compiler().defaultValue("");
		Template template = compiler.compile(templateAsString);

		Map<String, Object> ctx = preparedData.toMap();

		StringWriter writer = new StringWriter();
		template.execute(ctx, writer);
		return writer.getBuffer().toString();
	}

	private String getTemplate(JsonObject exportResponse) {
		String htmlTemplateFile;
		ExportRequest.View view = getView(exportResponse);
		switch (view) {
			case DAY:
				htmlTemplateFile = DAY_HTML_TEMPLATE;
				break;
			case LIST:
				htmlTemplateFile = LIST_HTML_TEMPLATE;
				break;
			case WEEK:
				htmlTemplateFile = WEEK_HTML_TEMPLATE;
				break;
			default:
				htmlTemplateFile = WEEK_HTML_TEMPLATE;
				break;
		}
		return htmlTemplateFile;
	}


}
