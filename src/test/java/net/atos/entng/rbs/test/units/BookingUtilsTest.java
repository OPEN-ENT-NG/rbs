package net.atos.entng.rbs.test.units;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import net.atos.entng.rbs.BookingUtils;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.*;

@RunWith(VertxUnitRunner.class)
public class BookingUtilsTest {

    @Test
    public void testIsLocalAdmin(TestContext ctx) {
        UserInfos userInfos = new UserInfos();
        userInfos.setFunctions(null);
        ctx.assertFalse(BookingUtils.isLocalAdmin(userInfos));
        Map<String, UserInfos.Function> map = new HashMap<>();
        userInfos.setFunctions(map);
        ctx.assertFalse(BookingUtils.isLocalAdmin(userInfos));
        map.put(DefaultFunctions.ADMIN_LOCAL, null);
        ctx.assertTrue(BookingUtils.isLocalAdmin(userInfos));
    }

    @Test
    public void testGetLocalAdminScope(TestContext ctx) {
        UserInfos userInfos = new UserInfos();
        Map<String, UserInfos.Function> map = new HashMap<>();
        userInfos.setFunctions(map);
        ctx.assertEquals(new ArrayList<>(), BookingUtils.getLocalAdminScope(userInfos));
        map.put(DefaultFunctions.ADMIN_LOCAL, null);
        ctx.assertEquals(new ArrayList<>(), BookingUtils.getLocalAdminScope(userInfos));
        UserInfos.Function function = new UserInfos.Function();
        List<String> list = Arrays.asList("string1", "string2");
        function.setScope(list);
        map.put(DefaultFunctions.ADMIN_LOCAL, function);
        ctx.assertEquals(list, BookingUtils.getLocalAdminScope(userInfos));
    }
}
