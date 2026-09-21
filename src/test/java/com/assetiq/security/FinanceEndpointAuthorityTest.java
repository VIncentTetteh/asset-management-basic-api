package com.assetiq.security;

import com.assetiq.controllers.v1.CloudAssetController;
import com.assetiq.controllers.v1.ExpenseController;
import com.assetiq.controllers.v1.LeaseRecordController;
import com.assetiq.controllers.v1.PurchaseOrderController;
import com.assetiq.security.annotation.RequireFreshMfa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Money-moving endpoints must be guarded by a manage/approve permission. Several
 * were reachable with a read permission (VIEW_REPORTS approved and deleted expenses,
 * VIEW_ASSETS recorded cloud cost), which a report viewer holds by default.
 */
@DisplayName("Finance endpoints require manage/approve authorities")
class FinanceEndpointAuthorityTest {

    private static final Pattern LITERAL = Pattern.compile("'([^']+)'");
    private static final Set<String> READ_ONLY = Set.of(
            "VIEW_REPORTS", "VIEW_ASSETS", "VIEW_BUDGETS", "VIEW_CONTRACTS", "VIEW_CLOUD_ASSETS",
            "VIEW_PROCUREMENT");

    @Test
    @DisplayName("expense approve/reject/delete need MANAGE_EXPENSES or APPROVE_BUDGET")
    void expenseMutations() throws Exception {
        assertThat(authorities(ExpenseController.class, "approve"))
                .containsExactlyInAnyOrder("ROLE_ORG_ADMIN", "ROLE_ADMIN", "MANAGE_EXPENSES", "APPROVE_BUDGET");
        assertThat(authorities(ExpenseController.class, "reject"))
                .containsExactlyInAnyOrder("ROLE_ORG_ADMIN", "ROLE_ADMIN", "MANAGE_EXPENSES", "APPROVE_BUDGET");
        assertThat(authorities(ExpenseController.class, "delete"))
                .containsExactlyInAnyOrder("ROLE_ORG_ADMIN", "ROLE_ADMIN", "MANAGE_EXPENSES");
        assertThat(method(ExpenseController.class, "approve").isAnnotationPresent(RequireFreshMfa.class)).isTrue();
    }

    @Test
    @DisplayName("recording cloud cost needs MANAGE_CLOUD_ASSETS")
    void cloudCost() throws Exception {
        assertThat(authorities(CloudAssetController.class, "recordCost"))
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_ORG_ADMIN", "MANAGE_CLOUD_ASSETS");
    }

    @Test
    @DisplayName("purchase-order workflow endpoints exist and approvals keep step-up MFA")
    void purchaseOrderWorkflow() throws Exception {
        assertThat(authorities(PurchaseOrderController.class, "submitPurchaseOrder")).contains("MANAGE_PROCUREMENT");
        assertThat(authorities(PurchaseOrderController.class, "receivePurchaseOrder")).contains("MANAGE_PROCUREMENT");
        assertThat(authorities(PurchaseOrderController.class, "cancelPurchaseOrder")).contains("MANAGE_PROCUREMENT");
        assertThat(method(PurchaseOrderController.class, "approvePurchaseOrder")
                .isAnnotationPresent(RequireFreshMfa.class)).isTrue();
        assertThat(method(PurchaseOrderController.class, "rejectPurchaseOrder")
                .isAnnotationPresent(RequireFreshMfa.class)).isTrue();
    }

    @Test
    @DisplayName("no mutating finance endpoint accepts a read-only authority")
    void noReadOnlyAuthorityOnMutations() {
        for (Class<?> controller : List.of(ExpenseController.class, CloudAssetController.class,
                LeaseRecordController.class, PurchaseOrderController.class)) {
            for (Method m : controller.getDeclaredMethods()) {
                if (!isMutation(m) || !m.isAnnotationPresent(PreAuthorize.class)) continue;
                assertThat(literals(m.getAnnotation(PreAuthorize.class).value()))
                        .describedAs("%s.%s", controller.getSimpleName(), m.getName())
                        .doesNotContainAnyElementsOf(READ_ONLY);
            }
        }
    }

    private static boolean isMutation(Method m) {
        return m.isAnnotationPresent(PostMapping.class) || m.isAnnotationPresent(PutMapping.class)
                || m.isAnnotationPresent(PatchMapping.class) || m.isAnnotationPresent(DeleteMapping.class);
    }

    private static List<String> authorities(Class<?> controller, String name) throws NoSuchMethodException {
        return literals(method(controller, name).getAnnotation(PreAuthorize.class).value());
    }

    private static Method method(Class<?> controller, String name) throws NoSuchMethodException {
        return Arrays.stream(controller.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(controller.getSimpleName() + "." + name));
    }

    private static List<String> literals(String expression) {
        Matcher m = LITERAL.matcher(expression);
        List<String> out = new java.util.ArrayList<>();
        while (m.find()) out.add(m.group(1));
        return out;
    }
}
