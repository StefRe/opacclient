package de.geeksfactory.opacclient.apis;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.geeksfactory.opacclient.objects.Account;
import de.geeksfactory.opacclient.objects.AccountData;

import static com.shazam.shazamcrest.MatcherAssert.assertThat;
import static com.shazam.shazamcrest.matcher.Matchers.sameBeanAs;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class SISISTest extends BaseHtmlTest {
    private SISIS sisis;

    @Before
    public void setUp() throws JSONException {
        sisis = spy(SISIS.class);
        sisis.opac_url = "https://opac.erfurt.de/webOPACClient";
        sisis.data = new JSONObject("{\"baseurl\":\"" + sisis.opac_url + "\"}");
    }

    @Test
    public void testLoadPages() throws IOException, OpacApi.OpacErrorException, JSONException {
        Account acc = new Account();
        // tests that links to other pages are also found when they are not visible from the
        // first page
        // (link to page 4 is usually not yet visible on page 1)
        String basedir = "/sisis/medialist/erfurt_pages/Katalog StuRB Erfurt Seite ";
        String html = readResource(basedir + "1.html");

        doNothing().when(sisis).start();
        doReturn(true).when(sisis).login(acc);

        doAnswer(invocation -> {
            String accountBase = "https://opac.erfurt.de/webOPACClient/userAccount.do?";
            String url = invocation.getArgument(0);
            if (url.equals(accountBase + "methodToCall=showAccount&typ=1")) {
                return html;
            } else if (url
                    .equals(accountBase + "methodToCall=pos&accountTyp=AUSLEIHEN&anzPos=11")) {
                return readResource(basedir + "2.html");
            } else if (url
                    .equals(accountBase + "methodToCall=pos&accountTyp=AUSLEIHEN&anzPos=21")) {
                return readResource(basedir + "3.html");
            } else if (url
                    .equals(accountBase + "methodToCall=pos&accountTyp=AUSLEIHEN&anzPos=31")) {
                return readResource(basedir + "4.html");
            } else if (url
                    .equals(accountBase + "methodToCall=pos&accountTyp=AUSLEIHEN&anzPos=41")) {
                return readResource(basedir + "5.html");
            } else if (url.equals(accountBase + "methodToCall=showAccount&typ=6") ||
                    url.equals(accountBase + "methodToCall=showAccount&typ=7")) {
                return "<table class=\"data\"><tr><td>keine Daten</td></tr></table>";
            } else {
                return null;
            }
        }).when(sisis).httpGet(anyString(), anyString());

        AccountData data = sisis.account(acc);

        assertEquals(43, data.getLent().size());
    }

    @Test
    public void testParseCoverJs() {
        String lampertheim = " var bookInfo = JSON.parse" +
                "('{\"ISBN:9783455010312\":{\"bib_key\":\"ISBN:9783455010312\"," +
                "\"info_url\":\"https://books.google" +
                ".com/books?id=q76xAgAACAAJ\\u0026source=gbs_ViewAPI\"," +
                "\"preview_url\":\"https://books.google" +
                ".com/books?id=q76xAgAACAAJ\\u0026source=gbs_ViewAPI\"," +
                "\"thumbnail_url\":\"https://books.google" +
                ".com/books/content?id=q76xAgAACAAJ\\u0026printsec=frontcover\\u0026img=1" +
                "\\u0026zoom=5\",\"preview\":\"noview\",\"embeddable\":false," +
                "\"can_download_pdf\":false,\"can_download_epub\":false," +
                "\"is_pdf_drm_enabled\":false,\"is_epub_drm_enabled\":false}}');\n" +
                "      var book = bookInfo[Object.keys(bookInfo)[0]]; \n" +
                "      \n" +
                "      \n" +
                "      var imgTag = '<img style=\"margin: 5px; border: 0px solid #666; ' + size +" +
                " '\" border=0 src=\"' + book.thumbnail_url + '\">';";
        String url = SISIS.parseCoverJs(lampertheim, "https://katalog.lampertheim.de");
        assertEquals("https://books.google.com/books/content?id=q76xAgAACAAJ&printsec" +
                "=frontcover&img=1&zoom=5", url);

        String wuppertal = "      var imgSrc = 'showMVBCover" +
                ".do?token=2aa75c57-40a7-4c99-b501-d49b39ada7a9';\n" +
                "      var imgTag = '<img style=\"margin: 10px; \" border=0 src=\"' + imgSrc + " +
                "'\"/>';\n" +
                "   var detailUrl = \"http://www.buchhandel.de/buch/9783551551931\";   \n" +
                "      var imgLink= '<a href=' + detailUrl + ' target=\"cover\">' + imgTag + " +
                "'</a>';\n" +
                "      $(\"div#-1_5\").html(imgLink);\n" +
                "    ";
        url = SISIS.parseCoverJs(wuppertal, "http://webopac.wuppertal.de");
        assertEquals(
                "http://webopac.wuppertal.de/showMVBCover.do?token=2aa75c57-40a7-4c99-b501-d49b39ada7a9",
                url);
    }

    @Test
    public void testGetAjaxCoverUrlRelPath() {
        // as found at Stadt- und Regionalbibliothek Erfurt
        String html = "    <!--\n" +
                "      $.ajax({\n" +
                "        url: 'jsp/result/cover.jsp?localImg=&isbns=%5B978-3-8317-3282-1%5D&asins=%5B%5D&size=medium&pos=-1_1',\n" +
                "        dataType: 'script'\n" +
                "      });\n" +
                "    //-->";
        String actual = sisis.getAjaxCoverUrl(html);
        assertEquals("https://opac.erfurt.de/webOPACClient/jsp/result/cover.jsp?localImg=&isbns=%5B978-3-8317-3282-1%5D&asins=%5B%5D&size=medium&pos=-1_1", actual);
    }

    @Test
    public void testGetAjaxCoverUrlAbsPath() {
        // as found at Städtischen Bibliotheken Dresden
        String html = "    <!--\n" +
                "      $.ajax({\n" +
                "        url: '/webOPACClient/jsp/result/cover.jsp?localImg=&isbns=%5B978-3-8317-3282-1%5D&asins=%5B%5D&size=medium&pos=cover_-1_1',\n" +
                "        dataType: 'script'\n" +
                "      });\n" +
                "    //-->";
        String actual = sisis.getAjaxCoverUrl(html);
        assertEquals("https://opac.erfurt.de/webOPACClient/jsp/result/cover.jsp?localImg=&isbns=%5B978-3-8317-3282-1%5D&asins=%5B%5D&size=medium&pos=cover_-1_1", actual);
    }

    @Test
    public void testGetAjaxCoverUrlNoPath() {
        // as found at Stadtbibliothek Riesa (cover images come from amazon)
        String actual = sisis.getAjaxCoverUrl("");
        assertNull(actual);
    }

    @Test
    public void testGetAjaxCoverUrlBadPath() {
        // made up example of url with unencoded space
        String html = "    <!--\n" +
                "      $.ajax({\n" +
                "        url: 'jsp/result/cover.jsp?localImg=&isbns= %5B978-3-8317-3282-1%5D&asins=%5B%5D&size=medium&pos=-1_1',\n" +
                "        dataType: 'script'\n" +
                "      });\n" +
                "    //-->";
        String actual = sisis.getAjaxCoverUrl(html);
        assertNull(actual);
    }

    @Test
    public void testProlongAll() throws IOException, OpacApi.OpacErrorException, JSONException {
        Account acc = new Account();
        String html = readResource("/sisis/medialist/Dresden-renew_all.html");

        doNothing().when(sisis).start();
        doReturn(true).when(sisis).login(acc);
        doReturn(null).when(sisis).account(acc);
        doReturn(html).when(sisis).httpGet(anyString(), anyString());

        List<Map<String, String>> results = new ArrayList<>();
        HashMap<String, String> hm1 = new HashMap<>();
        hm1.put(OpacApi.ProlongAllResult.KEY_LINE_TITLE, "On the come up");
        hm1.put(OpacApi.ProlongAllResult.KEY_LINE_AUTHOR, "Thomas, Angie ¬[Verfasser]");
        hm1.put(OpacApi.ProlongAllResult.KEY_LINE_MESSAGE, "Keine Verlängerung, da maximale Anzahl Verlängerungen = 1 erreicht !");
        hm1.put(OpacApi.ProlongAllResult.KEY_LINE_NEW_RETURNDATE, "17.05.2021");
        results.add(hm1);
        HashMap<String, String> hm2 = new HashMap<>();
        hm2.put(OpacApi.ProlongAllResult.KEY_LINE_TITLE, "LandIdee 21/1");
        hm2.put(OpacApi.ProlongAllResult.KEY_LINE_MESSAGE, "Keine Verlängerung, da maximale Anzahl Verlängerungen = 1 erreicht !");
        hm2.put(OpacApi.ProlongAllResult.KEY_LINE_NEW_RETURNDATE, "16.04.2021");
        results.add(hm2);
        OpacApi.ProlongAllResult expected = new OpacApi.ProlongAllResult(OpacApi.MultiStepResult.Status.OK, results);

        OpacApi.ProlongAllResult actual = sisis.prolongAll(acc, 0, null);
        assertThat(actual, sameBeanAs(expected));
    }
}
