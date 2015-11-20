package controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.TemplateLoader;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

import helpers.ResourceTemplateLoader;
import play.Logger;
import play.Play;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by fo on 16.11.15.
 */
public class Application extends Controller {

  private static Map<String, String> mimeTypeExtensionMap = new HashMap<>();
  static {
    mimeTypeExtensionMap.put("text/turtle", "ttl");
    mimeTypeExtensionMap.put("application/ld+json", "jsonld");
    mimeTypeExtensionMap.put("application/json", "jsonld");
  }

  private static Model vocab = ModelFactory.createDefaultModel().read(Play.application().classloader()
      .getResourceAsStream(Play.application().configuration().getString("rs.source.ttl")), null, "TURTLE");

  public static Result getVocab(String version) {

    if (request().accepts("text/html")) {
      return redirect(routes.Application.getVocabPage(version).absoluteURL(request()));
    } else {
      return redirect(routes.Application.getVocabData(version, null).absoluteURL(request()));
    }

  }

  public static Result getVocabData(String version, String ext) {

    MimeType mimeType = getMimeType(request(), ext);
    response().setHeader("Content-Location", routes.Application.getVocabData(version,
        mimeTypeExtensionMap.get(mimeType.toString())).absoluteURL(request()));
    response().setHeader("Link", "<".concat(routes.Application.getVocabData(version, null)
        .absoluteURL(request())).concat(">; rel=derivedfrom"));

    return getData(vocab, mimeType);

  }

  public static Result getVocabPage(String version) throws IOException {

    response().setHeader("Link", "<".concat(routes.Application.getVocabPage(version)
        .absoluteURL(request())).concat(">; rel=derivedfrom"));

    return getPage(vocab, "vocab.hbs");

  }

  public static Result getStatement(String id, String version) {

    if (request().accepts("text/html")) {
      return redirect(routes.Application.getStatementPage(id, version).absoluteURL(request()));
    } else {
      return redirect(routes.Application.getStatementData(id, version, null).absoluteURL(request()));
    }

  }

  public static Result getStatementData(String id, String version, String ext) throws IOException {

    Model rightsStatement = getStatementModel(id, version);

    if (rightsStatement.isEmpty()) {
      return notFound();
    }

    MimeType mimeType = getMimeType(request(), ext);
    response().setHeader("Content-Location", routes.Application.getStatementData(id, version,
        mimeTypeExtensionMap.get(mimeType.toString())).absoluteURL(request()));
    response().setHeader("Link", "<".concat(routes.Application.getStatementData(id, version, null)
        .absoluteURL(request())).concat(">; rel=derivedfrom"));

    return getData(rightsStatement, mimeType);

  }

  public static Result getStatementPage(String id, String version) throws IOException {

    Model rightsStatement = getStatementModel(id, version);

    if (rightsStatement.isEmpty()) {
      return notFound();
    }

    response().setHeader("Link", "<".concat(routes.Application.getStatementPage(id, version)
        .absoluteURL(request())).concat(">; rel=derivedfrom"));

    return getPage(rightsStatement, "statement.hbs");

  }

  private static Result getData(Model model, MimeType mimeType) {

    OutputStream result = new ByteArrayOutputStream();

    switch (mimeType.toString()) {
      case "text/turtle":
        model.write(result, "TURTLE");
        break;
      case "application/ld+json":
      case "application/json":
        model.write(result, "JSON-LD");
        break;
      default:
        return status(406);
    }

    return ok(result.toString()).as(mimeType.toString());

  }

  private static Result getPage(Model model, String templateFile) throws IOException {

    OutputStream boas = new ByteArrayOutputStream();
    model.write(boas, "JSON-LD");
    Map<?,?> scope = new ObjectMapper().readValue(boas.toString(), HashMap.class);

    TemplateLoader loader = new ResourceTemplateLoader();
    loader.setPrefix("public/handlebars");
    loader.setSuffix("");
    Handlebars handlebars = new Handlebars(loader);

    try {
      handlebars.registerHelpers(new File("public/js/helpers.js"));
    } catch (Exception e) {
      Logger.error(e.toString());
    }

    Template pageTemplate = handlebars.compile(templateFile);
    Map<String, Object> main = new HashMap<>();
    main.put("content", pageTemplate.apply(scope));
    Template template = handlebars.compile("main.hbs");

    return ok(template.apply(main)).as("text/html");

  }

  private static Model getStatementModel(String id, String version) {

    String statementURI = "http://rightsstatements.org/vocab/%s/%s/";
    String constructStatement = "CONSTRUCT WHERE {<%1$s> ?p ?o}";
    Model rightsStatement = ModelFactory.createDefaultModel();
    QueryExecutionFactory.create(QueryFactory.create(String.format(constructStatement,
        String.format(statementURI, id, version))), vocab).execConstruct(rightsStatement);

    return rightsStatement;

  }

  private static MimeType getMimeType(Http.Request request, String ext) {

    MimeType mimeType;

    try {
      if (ext != null) {
        switch (ext) {
          case "ttl":
            mimeType = new MimeType("text/turtle");
            break;
          case "jsonld":
            mimeType = new MimeType("application/ld+json");
            break;
          default:
            mimeType = new MimeType("text/html");
            break;
        }
      } else {
        mimeType = new MimeType(request.acceptedTypes().get(0).toString());
      }
    } catch (MimeTypeParseException e) {
      Logger.error(e.toString());
      mimeType = new MimeType();
    }

    return mimeType;

  }

}
