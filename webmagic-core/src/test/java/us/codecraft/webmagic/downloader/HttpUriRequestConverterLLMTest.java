package us.codecraft.webmagic.downloader;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.model.HttpRequestBody;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for {@link HttpUriRequestConverter} focusing solely on converting
 * {@link us.codecraft.webmagic.Request}/{@link us.codecraft.webmagic.Site}
 * into {@link org.apache.http.client.methods.HttpUriRequest}.
 *
 * These tests do not execute any HTTP calls.
 */
public class HttpUriRequestConverterLLMTest {

    /** Helper that always uses the 3-arg convert(...) available in this module. */
    private HttpClientRequestContext convert(Request request, Site site) {
        return new HttpUriRequestConverter().convert(request, site, null);
    }

    @Test
    public void convert_get_buildsQuery() {
        // given
        Request req = new Request("http://example.com/path?foo=1&bar=2").setMethod("GET");
        Site site = Site.me();

        // when
        HttpClientRequestContext ctx = convert(req, site);
        HttpUriRequest http = ctx.getHttpUriRequest();

        // then
        assertEquals("GET", http.getMethod()); // Confirms HTTP method is GET
        String query = http.getURI().getQuery();
        assertNotNull(query); // Ensures query string exists
        assertTrue(query.contains("foo=1")); // Ensures 'foo=1' param is present
        assertTrue(query.contains("bar=2")); // Ensures 'bar=2' param is present
        assertFalse(http instanceof HttpEntityEnclosingRequest); // GET must not carry a request entity
    }

    @Test
    public void convert_post_setsEntity() throws Exception {
        // given
        Request req = new Request("http://example.com/submit").setMethod("POST");
        Map<String, Object> form = new LinkedHashMap<>(); // LinkedHashMap for deterministic param order
        form.put("a", "1");
        form.put("b", "2");
        req.setRequestBody(HttpRequestBody.form(form, StandardCharsets.UTF_8.name()));
        Site site = Site.me();

        // when
        HttpClientRequestContext ctx = convert(req, site);
        HttpUriRequest http = ctx.getHttpUriRequest();

        // then
        assertEquals("POST", http.getMethod()); // Confirms HTTP method is POST
        assertTrue(http instanceof HttpEntityEnclosingRequest); // POST should carry an entity
        HttpEntity entity = ((HttpEntityEnclosingRequest) http).getEntity();
        assertNotNull(entity); // Ensures entity is actually present
        String body = EntityUtils.toString(entity, StandardCharsets.UTF_8); // Reads entity content (no network)
        assertTrue(body.contains("a=1")); // Confirms form param 'a=1' serialized into body
        assertTrue(body.contains("b=2")); // Confirms form param 'b=2' serialized into body
        Header ct = entity.getContentType();
        assertNotNull(ct); // Ensures a Content-Type is set on the entity
        String ctVal = ct.getValue();
        assertTrue(ctVal.toLowerCase().contains("application/x-www-form-urlencoded")); // Form content type is correct
        assertTrue(ctVal.toLowerCase().contains("charset=utf-8")); // Charset propagated to Content-Type
    }

    @Test
    public void convert_headers_propagate_and_request_overrides_site() {
        // given
        String url = "http://example.com/";
        Site site = Site.me()
                .addHeader("Header-Only-Site", "sitev")          // header only at Site level
                .addHeader("Header-Common", "site-common");      // header with same name at both levels
        Request req = new Request(url)
                .addHeader("Header-Only-Request", "reqv")        // header only at Request level
                .addHeader("Header-Common", "req-common");       // request-level value intended to take precedence

        // when
        HttpClientRequestContext ctx = convert(req, site);
        HttpUriRequest http = ctx.getHttpUriRequest();

        // then
        assertEquals("sitev", http.getFirstHeader("Header-Only-Site").getValue()); // Site-only header is present
        assertEquals("reqv", http.getFirstHeader("Header-Only-Request").getValue()); // Request-only header is present

        Header[] commons = http.getHeaders("Header-Common");
        assertEquals(2, commons.length); // Both Site and Request values are present for common header
        assertEquals("site-common", commons[0].getValue()); // Site header is added first
        assertEquals("req-common", commons[1].getValue()); // Request header is added after and thus takes precedence
    }

    @Test
    public void convert_siteCookies_notAppliedByConverter_documented() {
        // given
        String url = "http://www.example.com/";
        Site site = Site.me().addCookie("example.com", "k", "v"); // Site-level cookie for matching domain
        Request req = new Request(url); // No per-request cookies set

        // when
        HttpClientRequestContext ctx = convert(req, site);
        HttpUriRequest http = ctx.getHttpUriRequest();

        // then
        assertNull(http.getFirstHeader("Cookie")); // Converter does not inject Site cookies into request headers
        // Converter only sets cookie store when Request has cookies; Site cookies are handled elsewhere (e.g., client generator)
        assertTrue(ctx.getHttpClientContext().getCookieStore() == null
                || ctx.getHttpClientContext().getCookieStore().getCookies().isEmpty()); // No CookieStore set from Site-only cookies
    }
}
