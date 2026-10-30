package com.opson.server.db

import com.opson.server.domain.CatalogSource
import com.opson.server.domain.FoodCategory
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Schema smoke test: proves Flyway created the table *and* that the Exposed
 * [ProfilesTable] projection matches the real columns (the `selectAll()`
 * references every declared column, so a drifted column name fails here). Add a
 * line per new table.
 */
class DatabaseSchemaTest : DatabaseTest() {

    @Test
    fun migrationsCreateProfilesTable() = runTest {
        val rows = database.transaction {
            ProfilesTable.selectAll().toList()
        }
        assertTrue(rows.isEmpty())
    }

    @Test
    fun everyOtherTableMatchesItsExposedProjection() = runTest {
        database.transaction {
            listOf(
                FoodsTable, OffFoodsTable, FoodSearchEventsTable, BarcodeLookupsTable, BarcodeMissesTable,
                LogEntriesTable, DayRecordsTable, MealFeelingsTable, WeighInsTable,
                CustomFoodsTable, SavedMealsTable, UserSettingsTable, FastsTable, ExercisesTable, WaitlistTable,
                AppleRefreshTokensTable, SubmittedFoodsTable, FoodSubmissionsTable, FoodSubmissionPhotosTable,
                BarcodeLinkSuggestionsTable, AppConfigValuesTable, AppConfigRulesTable, AppConfigAuditTable,
                AppConfigManifestTable, UserRolesTable, EntitlementGrantsTable, AccessAuditTable,
                FoodReportsTable, FoodReportPhotosTable, FoodCorrectionsTable,
                BuilderMembersTable, FoodVerificationsTable, FoodProvenanceTable,
                RecipesTable, RecipeReportsTable,
            ).forEach { table -> table.selectAll().limit(1).toList() }
        }
    }

    @Test
    fun rowLevelSecurityIsOnForEveryTable() {
        // On Supabase the anon key can reach any public table PostgREST is granted, and
        // tables the postgres role creates are granted by default. RLS with no policies
        // is what keeps them closed (V19 covers the tables created before the rule).
        val withoutRls = strings(
            "SELECT relname FROM pg_class WHERE relnamespace = 'public'::regnamespace AND relkind = 'r' AND NOT relrowsecurity",
        )
        assertEquals(listOf("flyway_schema_history"), withoutRls, "RLS off on $withoutRls")
    }

    @Test
    fun everySyncedTableHasTheCursorTrigger() {
        val triggers = strings("SELECT tgname FROM pg_trigger WHERE NOT tgisinternal")
        listOf("log_entries", "day_records", "meal_feelings", "weigh_ins", "custom_foods", "saved_meals", "user_settings", "fasts", "exercises", "recipes")
            .forEach { assertTrue("${it}_bump_server_seq" in triggers, "no cursor trigger on $it") }
    }

    @Test
    fun foodTiersFollowTheSourceForEverySourceTheCheckAllows() {
        assertTrue("foods_set_tier" in strings("SELECT tgname FROM pg_trigger WHERE NOT tgisinternal"))
        // The Kotlin enum and the SQL function must agree, or a cached food
        // would be served with one tier and searched with another.
        val sources = CatalogSource.entries.joinToString(",") { "'${it.wire}'" }
        val tiers = strings("SELECT s || '=' || public.food_source_tier(s) FROM unnest(ARRAY[$sources]) AS s")
        assertEquals(CatalogSource.entries.map { "${it.wire}=${it.tier.wire}" }, tiers)
    }

    /**
     * V12 was applied to opson-dev by hand before Flyway saw it, so running
     * it over an existing table must change nothing. The monthly reload's
     * swap renames indexes by their `off_foods_` prefix, so every index has
     * to carry it.
     */
    @Test
    fun offFoodsMigrationIsIdempotentAndItsIndexesCarryThePrefix() {
        val indexes = "SELECT indexname::text FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'off_foods' ORDER BY 1"
        val before = strings(indexes)
        assertEquals(
            listOf(
                "off_foods_barcode_key", "off_foods_countries_idx", "off_foods_country_search_idx", "off_foods_name_idx",
                "off_foods_name_trgm_idx", "off_foods_pkey", "off_foods_search_idx",
            ),
            before,
        )
        listOf("V12__off_foods.sql", "V15__search_quality.sql").forEach { file ->
            val migration = checkNotNull(javaClass.getResource("/db/migration/$file")).readText()
            database.blockingTransaction { exec(migration) }
        }
        assertEquals(before, strings(indexes))
    }

    /**
     * The Open Food Facts reload swaps `off_foods` by renaming it and dropping the old table. A
     * view follows the rename and blocks the drop, so nothing may depend on the table: the V33
     * analytics read it through functions instead.
     */
    @Test
    fun noViewDependsOnOffFoods() {
        val views = strings(
            "SELECT DISTINCT r.ev_class::regclass::text FROM pg_depend d JOIN pg_rewrite r ON r.oid = d.objid " +
                "WHERE d.refobjid = 'public.off_foods'::regclass AND r.ev_class <> 'public.off_foods'::regclass",
        )
        assertEquals(emptyList(), views)
    }

    /**
     * V17 was applied to opson-dev by hand before Flyway saw it, like V12, so
     * a second run changes nothing. The Kotlin enum and the CHECK must agree,
     * or the backfill could write a value the server can't read back.
     */
    @Test
    fun foodCategoryMigrationIsIdempotentAndItsCheckMatchesTheEnum() {
        val migration = checkNotNull(javaClass.getResource("/db/migration/V17__food_category.sql")).readText()
        database.blockingTransaction { exec(migration) }

        assertEquals(
            listOf("1"),
            strings("SELECT count(*)::text FROM pg_constraint WHERE conname = 'foods_category_check'"),
        )
        FoodCategory.entries.forEach { category ->
            database.blockingTransaction {
                exec(
                    "INSERT INTO foods (source, source_id, name, category) " +
                        "VALUES ('usda_sr', 'category-${category.wire}', 'x', '${category.wire}')",
                )
            }
        }
        assertFailsWith<Exception> {
            database.blockingTransaction {
                exec("INSERT INTO foods (source, source_id, name, category) VALUES ('usda_sr', 'category-bad', 'x', 'snack')")
            }
        }
        database.blockingTransaction { exec("DELETE FROM foods WHERE source_id LIKE 'category-%'") }
    }

    /** V14 (fasts) is additive and safe to run twice; account deletion still cascades into it. */
    @Test
    fun fastsMigrationIsIdempotentAndCascadesFromTheUser() {
        val migration = checkNotNull(javaClass.getResource("/db/migration/V14__fasts.sql")).readText()
        database.blockingTransaction { exec(migration) }

        val user = seedAuthUser().value
        database.blockingTransaction {
            exec("INSERT INTO fasts (user_id, day, started_at, goal_hours, client_updated_at) VALUES ('$user', '2026-10-25', now(), 14, now())")
        }
        assertEquals(listOf("1"), strings("SELECT count(*)::text FROM fasts WHERE user_id = '$user'"))
        database.blockingTransaction { exec("DELETE FROM auth.users WHERE id = '$user'") }
        assertEquals(listOf("0"), strings("SELECT count(*)::text FROM fasts WHERE user_id = '$user'"))
    }

    /** The cap holds in the table too, not only in DiaryRules. */
    @Test
    fun fastsRefuseAGoalOrALengthPast16Hours() {
        val user = seedAuthUser().value
        listOf(
            "INSERT INTO fasts (user_id, day, started_at, goal_hours, client_updated_at) VALUES ('$user', '2026-10-25', now(), 18, now())",
            "INSERT INTO fasts (user_id, day, started_at, ended_at, client_updated_at) " +
                "VALUES ('$user', '2026-10-25', now(), now() + interval '17 hours', now())",
        ).forEach { sql ->
            assertFailsWith<Exception>(sql) { database.blockingTransaction { exec(sql) } }
        }
    }

    /** V24 (exercises) is additive and safe to run twice; account deletion still cascades into it. */
    @Test
    fun exercisesMigrationIsIdempotentAndCascadesFromTheUser() {
        val migration = checkNotNull(javaClass.getResource("/db/migration/V24__exercises.sql")).readText()
        database.blockingTransaction { exec(migration) }

        val user = seedAuthUser().value
        database.blockingTransaction {
            exec(
                "INSERT INTO exercises (id, user_id, day, started_at, activity, minutes, kcal, client_updated_at) " +
                    "VALUES (gen_random_uuid(), '$user', '2026-10-25', now(), 'walking', 30, 120, now())",
            )
        }
        assertEquals(listOf("1"), strings("SELECT count(*)::text FROM exercises WHERE user_id = '$user'"))
        database.blockingTransaction { exec("DELETE FROM auth.users WHERE id = '$user'") }
        assertEquals(listOf("0"), strings("SELECT count(*)::text FROM exercises WHERE user_id = '$user'"))
    }

    /**
     * V35 is additive and safe to run twice. Deleting an account deletes its recipes and the reports
     * it made; a recipe's reports go with the recipe. Codes are eight of the readable characters.
     */
    @Test
    fun recipesMigrationIsIdempotentAndCascadesFromTheUser() {
        val migration = checkNotNull(javaClass.getResource("/db/migration/V35__recipes.sql")).readText()
        database.blockingTransaction { exec(migration) }

        val owner = seedAuthUser().value
        val reporter = seedAuthUser().value
        val recipe = java.util.UUID.randomUUID()
        database.blockingTransaction {
            exec(
                "INSERT INTO recipes (id, user_id, name, servings, client_updated_at, public_code, public_state) " +
                    "VALUES ('$recipe', '$owner', 'Chili', 6, now(), 'abcd2345', 'live')",
            )
            exec("INSERT INTO recipe_reports (id, recipe_id, user_id, reason) VALUES (gen_random_uuid(), '$recipe', '$reporter', 'spam')")
        }
        assertFailsWith<Exception> {
            database.blockingTransaction {
                exec("INSERT INTO recipes (id, user_id, name, servings, client_updated_at, public_code) VALUES (gen_random_uuid(), '$owner', 'x', 1, now(), 'ABCD2345')")
            }
        }
        database.blockingTransaction { exec("DELETE FROM auth.users WHERE id = '$reporter'") }
        assertEquals(listOf("0"), strings("SELECT count(*)::text FROM recipe_reports WHERE recipe_id = '$recipe'"))
        database.blockingTransaction { exec("DELETE FROM auth.users WHERE id = '$owner'") }
        assertEquals(listOf("0"), strings("SELECT count(*)::text FROM recipes WHERE user_id = '$owner'"))
    }

    /**
     * V25 is additive and safe to run twice. Deleting an account deletes its
     * submissions and photos, and the food with them unless a reviewer
     * approved it: an approved food is catalog data and stays.
     */
    @Test
    fun foodSubmissionsMigrationIsIdempotentAndAccountDeletionKeepsOnlyApprovedFoods() {
        val migration = checkNotNull(javaClass.getResource("/db/migration/V25__food_submissions.sql")).readText()
        database.blockingTransaction { exec(migration) }

        val user = seedAuthUser().value
        val pendingFood = java.util.UUID.randomUUID()
        val approvedFood = java.util.UUID.randomUUID()
        database.blockingTransaction {
            listOf(pendingFood to "pending", approvedFood to "approved").forEach { (food, status) ->
                exec(
                    "INSERT INTO foods (id, source, source_id, name, review_status) " +
                        "VALUES ('$food', 'user_submitted', '$food', 'x', '$status')",
                )
                exec(
                    "INSERT INTO food_submissions (id, user_id, food_id, status, origin, submitted) " +
                        "VALUES (gen_random_uuid(), '$user', '$food', '$status', 'barcode', '{}'::jsonb)",
                )
            }
            exec(
                "INSERT INTO food_submission_photos (submission_id, kind, media_type, bytes) " +
                    "SELECT id, 'front', 'image/jpeg', decode('ffd8', 'hex') FROM food_submissions WHERE user_id = '$user'",
            )
        }
        assertEquals(listOf("2"), strings("SELECT count(*)::text FROM foods WHERE id IN ('$pendingFood', '$approvedFood')"))
        database.blockingTransaction { exec("DELETE FROM auth.users WHERE id = '$user'") }

        assertEquals(listOf("0"), strings("SELECT count(*)::text FROM food_submissions WHERE user_id = '$user'"))
        assertEquals(listOf("0"), strings("SELECT count(*)::text FROM food_submission_photos p LEFT JOIN food_submissions s ON s.id = p.submission_id WHERE s.id IS NULL"))
        assertEquals(listOf(approvedFood.toString()), strings("SELECT id::text FROM foods WHERE id IN ('$pendingFood', '$approvedFood')"))
        assertFailsWith<Exception> {
            database.blockingTransaction { exec("UPDATE foods SET review_status = 'maybe' WHERE id = '$approvedFood'") }
        }
        database.blockingTransaction { exec("DELETE FROM foods WHERE id = '$approvedFood'") }
    }

    /** V27 is additive and safe to run twice; the link to the submitter's own copy is optional. */
    @Test
    fun submissionCustomFoodMigrationIsIdempotentAndOptional() {
        val migration = checkNotNull(javaClass.getResource("/db/migration/V27__food_submission_custom_food.sql")).readText()
        database.blockingTransaction { exec(migration) }

        assertEquals(
            listOf("uuid YES"),
            strings(
                "SELECT data_type || ' ' || is_nullable FROM information_schema.columns " +
                    "WHERE table_name = 'food_submissions' AND column_name = 'custom_food_id'",
            ),
        )
    }

    /**
     * V31 is additive and safe to run twice. Deleting an account deletes its Builder row and its
     * checks against the pack; provenance outlives it with the account's key and no id.
     */
    @Test
    fun buildersMigrationIsIdempotentAndProvenanceOutlivesTheAccount() {
        val migration = checkNotNull(javaClass.getResource("/db/migration/V31__builders.sql")).readText()
        database.blockingTransaction { exec(migration) }

        val user = seedAuthUser().value
        val food = java.util.UUID.randomUUID()
        database.blockingTransaction {
            exec("INSERT INTO builder_members (user_id, terms_version) VALUES ('$user', 't')")
            exec(
                "INSERT INTO food_verifications (id, food_id, user_id, outcome, grounded, groundable) " +
                    "VALUES (gen_random_uuid(), '$food', '$user', 'confirmed', 4, 4)",
            )
            exec("INSERT INTO food_provenance (food_id, kind, ref_id, user_id) VALUES ('$food', 'submission', gen_random_uuid(), '$user')")
        }
        assertFailsWith<Exception>("a second confirmation of the same food") {
            database.blockingTransaction {
                exec(
                    "INSERT INTO food_verifications (id, food_id, user_id, outcome, grounded, groundable) " +
                        "VALUES (gen_random_uuid(), '$food', '$user', 'confirmed', 4, 4)",
                )
            }
        }
        val key = strings("SELECT subject_key FROM food_provenance WHERE food_id = '$food'").single()
        assertEquals(64, key.length)

        database.blockingTransaction { exec("DELETE FROM auth.users WHERE id = '$user'") }
        assertEquals(listOf("0"), strings("SELECT count(*)::text FROM builder_members WHERE user_id = '$user'"))
        assertEquals(listOf("0"), strings("SELECT count(*)::text FROM food_verifications WHERE food_id = '$food'"))
        assertEquals(listOf(key), strings("SELECT subject_key FROM food_provenance WHERE food_id = '$food' AND user_id IS NULL"))
        database.blockingTransaction { exec("DELETE FROM food_provenance WHERE food_id = '$food'") }
    }

    /**
     * V32 is additive and safe to run twice. One application can be pending per account, any
     * number decided, and deleting the account deletes them all.
     */
    @Test
    fun builderApplicationsMigrationIsIdempotentAllowsOnePendingAndCascadesFromTheUser() {
        val migration = checkNotNull(javaClass.getResource("/db/migration/V32__builder_applications.sql")).readText()
        database.blockingTransaction { exec(migration) }

        val user = seedAuthUser().value
        fun insert(status: String) = database.blockingTransaction {
            exec("INSERT INTO builder_applications (id, user_id, status, terms_version) VALUES (gen_random_uuid(), '$user', '$status', 't')")
        }
        insert("declined")
        insert("withdrawn")
        insert("pending")
        assertFailsWith<Exception>("a second pending application") { insert("pending") }
        assertFailsWith<Exception>("an unknown status") { insert("maybe") }
        assertEquals(listOf("3"), strings("SELECT count(*)::text FROM builder_applications WHERE user_id = '$user'"))

        database.blockingTransaction { exec("DELETE FROM auth.users WHERE id = '$user'") }
        assertEquals(listOf("0"), strings("SELECT count(*)::text FROM builder_applications WHERE user_id = '$user'"))
    }

    /**
     * V29 is additive and safe to run twice. Deleting an account deletes its reports and their
     * photos; the server's own energy checks have no account and are one per food; a food has one
     * live correction at a time.
     */
    @Test
    fun foodReportsMigrationIsIdempotentAndCascadesFromTheUser() {
        val migration = checkNotNull(javaClass.getResource("/db/migration/V29__food_reports.sql")).readText()
        database.blockingTransaction { exec(migration) }

        val user = seedAuthUser().value
        val food = java.util.UUID.randomUUID()
        val snapshot = "'{\"name\": \"x\", \"source\": \"usda_branded\"}'::jsonb"
        database.blockingTransaction {
            exec(
                "INSERT INTO food_reports (id, food_id, user_id, reason, snapshot) " +
                    "VALUES (gen_random_uuid(), '$food', '$user', 'wrong_calories', $snapshot)",
            )
            exec(
                "INSERT INTO food_report_photos (report_id, media_type, bytes) " +
                    "SELECT id, 'image/jpeg', decode('ffd8', 'hex') FROM food_reports WHERE user_id = '$user'",
            )
            exec("INSERT INTO food_reports (id, food_id, reason, snapshot) VALUES (gen_random_uuid(), '$food', 'energy_check', $snapshot)")
        }
        listOf(
            // Only the server files an energy check, and only once per food.
            "INSERT INTO food_reports (id, food_id, reason, snapshot) VALUES (gen_random_uuid(), '$food', 'energy_check', $snapshot)",
            "INSERT INTO food_reports (id, food_id, reason, snapshot) VALUES (gen_random_uuid(), '$food', 'other', $snapshot)",
            "INSERT INTO food_reports (id, food_id, user_id, reason, snapshot) VALUES (gen_random_uuid(), '$food', '$user', 'tasty', $snapshot)",
        ).forEach { sql -> assertFailsWith<Exception>(sql) { database.blockingTransaction { exec(sql) } } }

        database.blockingTransaction {
            exec("INSERT INTO food_corrections (id, food_id, created_by, fields, base) VALUES (gen_random_uuid(), '$food', 'r', '{}', $snapshot)")
        }
        assertFailsWith<Exception>("two live corrections") {
            database.blockingTransaction {
                exec("INSERT INTO food_corrections (id, food_id, created_by, fields, base) VALUES (gen_random_uuid(), '$food', 'r', '{}', $snapshot)")
            }
        }

        database.blockingTransaction { exec("DELETE FROM auth.users WHERE id = '$user'") }
        assertEquals(listOf("energy_check"), strings("SELECT reason FROM food_reports WHERE food_id = '$food'"))
        assertEquals(listOf("0"), strings("SELECT count(*)::text FROM food_report_photos"))
        database.blockingTransaction {
            exec("DELETE FROM food_reports WHERE food_id = '$food'")
            exec("DELETE FROM food_corrections WHERE food_id = '$food'")
        }
    }

    /**
     * V34 is additive and safe to run twice. A verification's photos go with it, and with the
     * account; a report takes several reasons from the known list and a front and a label photo;
     * accepting a verification records its provenance.
     */
    @Test
    fun checksAndReviewMigrationIsIdempotentAndCascadesFromTheUser() {
        val migration = checkNotNull(javaClass.getResource("/db/migration/V34__food_checks_and_review.sql")).readText()
        database.blockingTransaction { exec(migration) }

        val user = seedAuthUser().value
        val food = java.util.UUID.randomUUID()
        val verification = java.util.UUID.randomUUID()
        val report = java.util.UUID.randomUUID()
        val snapshot = "'{\"name\": \"x\", \"source\": \"usda_branded\"}'::jsonb"
        val jpeg = "'image/jpeg', decode('ffd8', 'hex')"
        database.blockingTransaction {
            exec(
                "INSERT INTO food_verifications (id, food_id, user_id, outcome, grounded, groundable, status, problem, snapshot) " +
                    "VALUES ('$verification', '$food', '$user', 'unconfirmed', 2, 4, 'open', '{\"wrong\": [\"calories\"]}', $snapshot)",
            )
            listOf("front", "label").forEach { kind ->
                exec("INSERT INTO food_verification_photos (verification_id, kind, media_type, bytes) VALUES ('$verification', '$kind', $jpeg)")
            }
            exec(
                "INSERT INTO food_reports (id, food_id, user_id, reason, reasons, snapshot) " +
                    "VALUES ('$report', '$food', '$user', 'wrong_numbers', ARRAY['wrong_numbers', 'missing_info'], $snapshot)",
            )
            listOf("front", "label").forEach { kind ->
                exec("INSERT INTO food_report_photos (report_id, kind, media_type, bytes) VALUES ('$report', '$kind', $jpeg)")
            }
        }
        listOf(
            "INSERT INTO food_verification_photos (verification_id, kind, media_type, bytes) VALUES ('$verification', 'side', $jpeg)",
            "UPDATE food_verifications SET status = 'maybe' WHERE id = '$verification'",
            "INSERT INTO food_reports (id, food_id, user_id, reason, reasons, snapshot) VALUES (gen_random_uuid(), '$food', '$user', 'other', ARRAY['tasty'], $snapshot)",
            "INSERT INTO food_report_photos (report_id, kind, media_type, bytes) VALUES ('$report', 'label', $jpeg)",
        ).forEach { sql -> assertFailsWith<Exception>(sql) { database.blockingTransaction { exec(sql) } } }

        database.blockingTransaction { exec("UPDATE food_verifications SET status = 'accepted', resolved_by = 'r', resolved_at = now() WHERE id = '$verification'") }
        assertEquals(listOf("verification"), strings("SELECT kind FROM food_provenance WHERE ref_id = '$verification'"))

        database.blockingTransaction { exec("DELETE FROM auth.users WHERE id = '$user'") }
        assertEquals(listOf("0"), strings("SELECT count(*)::text FROM food_verification_photos WHERE verification_id = '$verification'"))
        assertEquals(listOf("0"), strings("SELECT count(*)::text FROM food_report_photos WHERE report_id = '$report'"))
        database.blockingTransaction { exec("DELETE FROM food_provenance WHERE food_id = '$food'") }
    }

    /** The limits DiaryRules checks hold in the table too. */
    @Test
    fun exercisesRefuseALengthOrANumberOutOfRange() {
        val user = seedAuthUser().value
        fun insert(minutes: Int, kcal: Int, activity: String = "walking") =
            "INSERT INTO exercises (id, user_id, day, started_at, activity, minutes, kcal, client_updated_at) " +
                "VALUES (gen_random_uuid(), '$user', '2026-10-25', now(), '$activity', $minutes, $kcal, now())"
        listOf(insert(0, 100), insert(1441, 100), insert(30, -1), insert(30, 5001), insert(30, 100, activity = "")).forEach { sql ->
            assertFailsWith<Exception>(sql) { database.blockingTransaction { exec(sql) } }
        }
    }

    @Test
    fun analyticsViewsAreQueryable() {
        ANALYTICS_VIEWS.forEach { view -> strings("SELECT 1::text FROM analytics.$view LIMIT 1") }
    }

    /** The structural half of "Grafana can never show one person's day" (system-design §9). */
    @Test
    fun noAnalyticsViewExposesAUserOrANutrientAmount() {
        val forbidden = setOf(
            "user_id", "kcal", "protein", "carbs", "fat", "fiber", "micros", "grams", "quantity",
            "kg", "water_ml", "note", "name", "settings", "feeling", "email",
        )
        val columns = strings("SELECT column_name::text FROM information_schema.columns WHERE table_schema = 'analytics'")
        assertTrue(columns.isNotEmpty())
        assertEquals(emptySet(), columns.toSet() intersect forbidden)
        assertEquals(ANALYTICS_VIEWS.toSet(), strings("SELECT table_name::text FROM information_schema.views WHERE table_schema = 'analytics'").toSet())
    }

    /** V11: a day marked untracked is a gap, so a coffee logged on a holiday is not a one-meal day. */
    @Test
    fun untrackedDaysLeaveMealsPerDay() {
        val tracked = seedAuthUser().value
        val holiday = seedAuthUser().value
        val day = "2026-10-25"
        database.blockingTransaction {
            listOf(tracked, holiday).forEach { user ->
                exec(
                    "INSERT INTO log_entries (id, user_id, day, slot, logged_at, name, precision, method, client_updated_at) " +
                        "VALUES (gen_random_uuid(), '$user', '$day', 'breakfast', now(), 'Coffee', 'estimate', 'search', now())",
                )
            }
            exec("INSERT INTO day_records (user_id, day, untracked, client_updated_at) VALUES ('$holiday', '$day', TRUE, now())")
        }
        try {
            val rows = strings("SELECT meals_per_day || ':' || person_days FROM analytics.meals_per_day_distribution WHERE day = '$day'")
            assertEquals(listOf("1:1"), rows)
        } finally {
            database.blockingTransaction {
                exec("DELETE FROM log_entries WHERE day = '$day'")
                exec("DELETE FROM day_records WHERE day = '$day'")
            }
        }
    }

    /** V13 is additive and idempotent: running it again over a loaded table changes nothing. */
    @Test
    fun barcodeMissesMigrationIsIdempotent() {
        val rows = "SELECT count(*)::text FROM barcode_misses"
        database.blockingTransaction {
            exec("INSERT INTO barcode_misses (id, barcode, platform) VALUES (gen_random_uuid(), '04963406', 'ios')")
        }
        try {
            val before = strings(rows)
            val migration = checkNotNull(javaClass.getResource("/db/migration/V13__barcode_misses.sql")).readText()
            database.blockingTransaction { exec(migration) }
            assertEquals(before, strings(rows))
        } finally {
            database.blockingTransaction { exec("DELETE FROM barcode_misses") }
        }
    }

    /** The coverage views: errors stay out of the hit rate, misses group on the GTIN, region defaults to unknown. */
    @Test
    fun barcodeCoverageViewsAggregate() {
        database.blockingTransaction {
            listOf("catalog", "off", "miss", "error").forEach { outcome ->
                exec("INSERT INTO barcode_lookups (at, barcode, outcome) VALUES ('2001-02-03T12:00:00Z', '049000028911', '$outcome')")
            }
            // A UPC-A and its EAN-13 are one product; the third has no region.
            exec(
                "INSERT INTO barcode_misses (id, barcode, gtin, country, platform) VALUES " +
                    "(gen_random_uuid(), '049000028911', '00049000028911', 'US', 'ios'), " +
                    "(gen_random_uuid(), '0049000028911', '00049000028911', 'CA', 'android'), " +
                    "(gen_random_uuid(), '4006381333931', '04006381333931', NULL, 'android')",
            )
        }
        try {
            assertEquals(
                listOf("4:2:1:1:0.6667"),
                strings(
                    "SELECT lookups || ':' || hits || ':' || misses || ':' || errors || ':' || hit_rate " +
                        "FROM analytics.barcode_hit_rate_daily WHERE day = '2001-02-03'",
                ),
            )
            assertEquals(
                listOf("00049000028911:2:2", "04006381333931:1:0"),
                strings("SELECT code || ':' || misses || ':' || countries FROM analytics.top_missed_barcodes"),
            )
            assertEquals(
                listOf("CA:1", "US:1", "unknown:1"),
                strings("SELECT country || ':' || misses FROM analytics.barcode_misses_by_country ORDER BY country"),
            )
        } finally {
            database.blockingTransaction {
                exec("DELETE FROM barcode_lookups WHERE at < '2002-01-01'")
                exec("DELETE FROM barcode_misses")
            }
        }
    }

    /** V17 is additive and idempotent: running it again over a loaded table changes nothing. */
    @Test
    fun waitlistMigrationIsIdempotent() {
        val rows = "SELECT count(*)::text FROM waitlist"
        database.blockingTransaction { exec("INSERT INTO waitlist (email, source) VALUES ('ada@example.com', 'home')") }
        try {
            val before = strings(rows)
            val migration = checkNotNull(javaClass.getResource("/db/migration/V16__waitlist.sql")).readText()
            database.blockingTransaction { exec(migration) }
            database.blockingTransaction { exec(migration) }
            assertEquals(before, strings(rows))
        } finally {
            database.blockingTransaction { exec("DELETE FROM waitlist") }
        }
    }

    /** The table refuses what the route would never write: a second address in another case, a free-form source. */
    @Test
    fun waitlistRefusesCaseDuplicatesAndBadSources() {
        try {
            database.blockingTransaction { exec("INSERT INTO waitlist (email) VALUES ('ada@example.com')") }
            assertFailsWith<Exception> {
                database.blockingTransaction { exec("INSERT INTO waitlist (email) VALUES ('ADA@example.com')") }
            }
            assertFailsWith<Exception> {
                database.blockingTransaction { exec("INSERT INTO waitlist (email, source) VALUES ('b@example.com', 'Home Page')") }
            }
        } finally {
            database.blockingTransaction { exec("DELETE FROM waitlist") }
        }
    }

    /** Sign-ups per UTC day, a count and nothing else. */
    @Test
    fun waitlistDailyCountsSignupsPerDay() {
        database.blockingTransaction {
            exec(
                "INSERT INTO waitlist (email, created_at) VALUES " +
                    "('a@example.com', '2001-02-03T01:00:00Z'), ('b@example.com', '2001-02-03T23:00:00Z'), " +
                    "('c@example.com', '2001-02-04T12:00:00Z')",
            )
        }
        try {
            assertEquals(
                listOf("2001-02-03:2", "2001-02-04:1"),
                strings("SELECT day || ':' || signups FROM analytics.waitlist_daily ORDER BY day"),
            )
            assertEquals(
                listOf("day", "signups"),
                strings(
                    "SELECT column_name::text FROM information_schema.columns " +
                        "WHERE table_schema = 'analytics' AND table_name = 'waitlist_daily' ORDER BY ordinal_position",
                ),
            )
        } finally {
            database.blockingTransaction { exec("DELETE FROM waitlist") }
        }
    }

    @Test
    fun grafanaRoleReadsAnalyticsAndNothingElse() {
        database.blockingTransaction {
            exec("SET LOCAL ROLE grafana_ro")
            ANALYTICS_VIEWS.forEach { exec("SELECT * FROM analytics.$it LIMIT 1") }
        }
        listOf(
            "log_entries", "user_settings", "weigh_ins", "foods", "off_foods", "food_search_events", "barcode_misses",
            "profiles", "waitlist", "apple_refresh_tokens", "food_submissions", "food_submission_photos",
            "barcode_link_suggestions", "food_reports", "food_report_photos", "food_corrections",
            "moderation_secrets", "moderation_actions", "moderation_events", "food_provenance", "device_sightings",
            "device_markers", "contribution_checks", "contribution_signals",
            "builder_members", "food_verifications", "builder_applications",
        ).forEach { table ->
            assertFailsWith<Exception>("grafana_ro must not read public.$table") {
                database.blockingTransaction {
                    exec("SET LOCAL ROLE grafana_ro")
                    exec("SELECT * FROM public.$table LIMIT 1")
                }
            }
        }
    }

    /** V22 is additive and safe to run twice (it is also applied by hand on dev), and cascades from the user. */
    @Test
    fun supportMigrationIsIdempotentAndCascadesFromTheUser() {
        val migration = checkNotNull(javaClass.getResource("/db/migration/V22__support_views.sql")).readText()
        database.blockingTransaction { exec(migration) }

        val user = seedAuthUser().value
        database.blockingTransaction {
            exec("INSERT INTO user_installs (user_id, install_id, platform, app_version, build_number) VALUES ('$user', 'i', 'ios', '0.2.0', 52)")
        }
        assertEquals(listOf("1"), strings("SELECT count(*)::text FROM support.user_installs WHERE user_id = '$user'"))
        database.blockingTransaction { exec("DELETE FROM auth.users WHERE id = '$user'") }
        assertEquals(listOf("0"), strings("SELECT count(*)::text FROM user_installs WHERE user_id = '$user'"))
    }

    /**
     * Grafana reads the per-person `support` views as well as `analytics`, and still no table: the
     * views are the allowlist, so the role must not reach around them.
     */
    @Test
    fun grafanaRoleReadsTheSupportViewsButNoTableBehindThem() {
        database.blockingTransaction {
            exec("SET LOCAL ROLE grafana_ro")
            SUPPORT_VIEWS.forEach { exec("SELECT * FROM support.$it LIMIT 1") }
        }
        assertEquals(
            SUPPORT_VIEWS.toSet(),
            strings("SELECT table_name::text FROM information_schema.views WHERE table_schema = 'support'").toSet(),
        )
        listOf("public.user_installs", "auth.users").forEach { table ->
            assertFailsWith<Exception>("grafana_ro must not read $table") {
                database.blockingTransaction {
                    exec("SET LOCAL ROLE grafana_ro")
                    exec("SELECT * FROM $table LIMIT 1")
                }
            }
        }
    }

    private fun exec(sql: String) {
        TransactionManager.current().exec(sql)
    }

    private fun strings(sql: String): List<String> = database.blockingTransaction {
        val out = mutableListOf<String>()
        TransactionManager.current().exec(sql) { rs -> while (rs.next()) out += rs.getString(1) }
        out
    }

    private companion object {
        val ANALYTICS_VIEWS = listOf(
            "daily_active_loggers", "entries_by_method_precision", "meals_per_day_distribution", "days_closed",
            "preset_distribution", "search_daily", "top_zero_result_queries", "barcode_outcomes_daily",
            "weekly_retention_cohorts", "retention_d1_d7_d30", "barcode_hit_rate_daily", "top_missed_barcodes",
            "barcode_misses_by_country", "waitlist_daily", "food_submissions_daily", "food_submission_queue",
            "barcode_link_queue", "food_report_queue",
            // V30, the Builders board.
            "contributions_daily", "contribution_queue", "contribution_flags_daily", "contribution_checks_daily",
            "moderation_daily", "top_reported_foods",
            // V31, the Builder program.
            "contribution_lanes_daily", "builder_program_daily",
            // V32, applications.
            "builder_applications_daily",
            // V33, energy against the macros.
            "food_energy_by_source", "food_energy_worst",
            // V34, the Database cleanup row.
            "food_verification_queue", "food_verifications_daily", "food_review_decisions_daily",
            // V35, recipes and sharing them.
            "recipes_daily", "recipe_sharing",
        )
        val SUPPORT_VIEWS = listOf(
            "users", "user_installs", "user_daily_logging", "user_daily_marks", "user_settings",
            "contributors", "photo_reuse", "moderation_actions", "builders", "builder_applications",
        )
    }
}
