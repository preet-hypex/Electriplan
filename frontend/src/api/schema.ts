/* eslint-disable */
/**
 * Generated from contracts/openapi.json by scripts/api-types.mjs.
 * Do not edit by hand: change the API, regenerate contracts/openapi.json
 * (see contracts/README.md), then run `npm run contracts`.
 */
export type paths = {
    "/api/files/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * A file of the company
         * @description The bytes, with their type. A file's contents never change, so it may be cached.
         *
         *     **Permission:** `company.view` (roles: owner, admin, builder, electrician, viewer).
         */
        get: operations["getFile"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/houses/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * A house, with its project and storeys
         * @description **Permission:** `company.view` (roles: owner, admin, builder, electrician, viewer).
         */
        get: operations["getHouse"];
        /**
         * Change a house's details
         * @description Send the `version` you read; a house changed since is refused (409).
         *
         *     **Permission:** `project.edit` (roles: owner, admin, builder).
         */
        put: operations["updateHouse"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/houses/{id}/archive": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Archive a house
         * @description **Permission:** `project.edit` (roles: owner, admin, builder).
         */
        post: operations["archiveHouse"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/houses/{id}/floor-plan": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * The house's floor plan, to edit
         * @description The draft if there is one, otherwise the newest version. 404 when the house has none yet.
         *
         *     **Permission:** `company.view` (roles: owner, admin, builder, electrician, viewer).
         */
        get: operations["openFloorPlan"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/houses/{id}/floor-plan/draft": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Save the floor plan as the house's draft
         * @description What the editor sends as it goes. Send the draft's `version` from the last open or save; leave it out when there is no draft yet. The plan is checked against contracts/floor-plan.schema.json.
         *
         *     **Permission:** `floor-plan.edit` (roles: owner, admin, builder, electrician).
         */
        put: operations["saveFloorPlanDraft"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/houses/{id}/floor-plan/uploads": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Upload a floor-plan image and analyse it into the draft
         * @description The image (JPG or PNG, up to 25 MB) is kept in the company's files; the analyser reads it, and its plan becomes the house's draft, pointing at the kept image. Send the draft's `version` when the house has a draft.
         *
         *     **Permission:** `floor-plan.edit` (roles: owner, admin, builder, electrician).
         */
        post: operations["uploadFloorPlanImage"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/houses/{id}/floor-plan/versions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * The house's floor-plan history, newest first
         * @description **Permission:** `company.view` (roles: owner, admin, builder, electrician, viewer).
         */
        get: operations["listFloorPlanVersions"];
        put?: never;
        /**
         * Save the draft as a version
         * @description Freezes the draft: read-only from now on, and the house's floor plan for designs. The next edit starts a new draft.
         *
         *     **Permission:** `floor-plan.edit` (roles: owner, admin, builder, electrician).
         */
        post: operations["saveFloorPlanVersion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/houses/{id}/floor-plan/versions/{versionNo}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * One floor-plan version, to look at
         * @description **Permission:** `company.view` (roles: owner, admin, builder, electrician, viewer).
         */
        get: operations["getFloorPlanVersion"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/houses/{id}/floor-plan/versions/{versionNo}/restore": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Make an earlier version the draft
         * @description Its contents replace the draft (send the draft's `version` when there is one).
         *
         *     **Permission:** `floor-plan.edit` (roles: owner, admin, builder, electrician).
         */
        post: operations["restoreFloorPlanVersion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/houses/{id}/restore": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Restore an archived house
         * @description **Permission:** `project.edit` (roles: owner, admin, builder).
         */
        post: operations["restoreHouse"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/houses/recent": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Houses worked on lately
         * @description Not archived, in projects that are not archived, most recently changed first: "continue where you left off".
         *
         *     **Permission:** `company.view` (roles: owner, admin, builder, electrician, viewer).
         */
        get: operations["listRecentHouses"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * The signed-in user
         * @description Who the access token is for, and the API's copy of their Supabase account (null until the next sync).
         */
        get: operations["getMe"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/organisations": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * The caller's companies
         * @description Active memberships of companies that are not closed, by name: what the company switcher offers.
         */
        get: operations["listMyCompanies"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/organisations/current": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * The company this request acts in
         * @description With the caller's role there and the permissions that role has, so the app shows only actions that will work.
         */
        get: operations["getCurrentCompany"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/organisations/current/licence": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * The company's licence and seats in use
         * @description **Permission:** `licence.view` (roles: owner, admin).
         */
        get: operations["getCurrentLicence"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/projects": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * The company's projects
         * @description Newest activity first (a change to the project or any of its houses). Archived projects only with `archived=true`.
         *
         *     **Permission:** `company.view` (roles: owner, admin, builder, electrician, viewer).
         */
        get: operations["listProjects"];
        put?: never;
        /**
         * Start a project
         * @description The reference (PRJ-000001...) is assigned in order. The state is required; a distributor must supply that state.
         *
         *     **Permission:** `project.edit` (roles: owner, admin, builder).
         */
        post: operations["createProject"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/projects/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * A project with its houses
         * @description **Permission:** `company.view` (roles: owner, admin, builder, electrician, viewer).
         */
        get: operations["getProject"];
        /**
         * Change a project's details
         * @description Send every field, and the `version` you read. A project changed since then is refused (409), not overwritten.
         *
         *     **Permission:** `project.edit` (roles: owner, admin, builder).
         */
        put: operations["updateProject"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/projects/{id}/archive": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Archive a project
         * @description It leaves the project list and becomes read-only, with its houses, until restored.
         *
         *     **Permission:** `project.edit` (roles: owner, admin, builder).
         */
        post: operations["archiveProject"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/projects/{id}/houses": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Add a house to a project
         * @description It starts awaiting its floor plan, with its ground floor ready.
         *
         *     **Permission:** `project.edit` (roles: owner, admin, builder).
         */
        post: operations["addHouse"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/projects/{id}/restore": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Restore an archived project
         * @description **Permission:** `project.edit` (roles: owner, admin, builder).
         */
        post: operations["restoreProject"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/reference/addresses": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Find an Australian address
         * @description Suggestions while typing a site address, from an OpenStreetMap geocoder (Photon). Fewer than 3 characters: none. House numbers are not always known: the person checks the suggestion.
         */
        get: operations["findAddresses"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/reference/distributors": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Electricity distributors
         * @description Every distributor, or only those in one state, for the project brief's distributor field.
         */
        get: operations["listDistributors"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
};
export type webhooks = Record<string, never>;
export type components = {
    schemas: {
        AddressSuggestion: {
            label: string;
            /** Format: double */
            latitude: number;
            /** Format: double */
            longitude: number;
            postcode: string | null;
            state: components["schemas"]["AustralianState"];
            street: string | null;
            suburb: string | null;
        };
        /** @enum {string} */
        AustralianState: "NSW" | "VIC" | "QLD" | "WA" | "SA" | "TAS" | "ACT" | "NT";
        CommitForm: {
            note?: string;
            /** Format: int32 */
            version?: number;
        };
        Current: {
            /** Format: uuid */
            id: string;
            licence: components["schemas"]["LicenceStatus"];
            name: string;
            permissions: components["schemas"]["Permission"][];
            role: components["schemas"]["MemberRole"];
            slug: string;
        };
        Distributor: {
            code: string;
            name: string;
            state: components["schemas"]["AustralianState"];
        };
        /** @enum {string} */
        DwellingType: "house" | "townhouse" | "unit" | "granny_flat" | "extension" | "other";
        ErrorMessage: {
            /** @description What went wrong, in words to show the user. */
            message: string;
        };
        FieldProblem: {
            field: string;
            message: string;
        };
        FloorPlanDocument: {
            /** Format: int32 */
            basedOnVersionNo: number | null;
            document: components["schemas"]["JsonNode"];
            /** Format: uuid */
            houseId: string;
            /** Format: uuid */
            levelId: string;
            /** Format: date-time */
            savedAt: string;
            /** Format: uuid */
            savedBy: string | null;
            state: components["schemas"]["FloorPlanState"];
            /** Format: int32 */
            version: number;
            /** Format: int32 */
            versionNo: number;
        };
        /** @enum {string} */
        FloorPlanOrigin: "analysis" | "editor" | "import";
        /** @enum {string} */
        FloorPlanState: "draft" | "committed";
        FloorPlanVersion: {
            /** Format: date-time */
            committedAt: string | null;
            current: boolean;
            floorAreaM2: number | null;
            note: string | null;
            /** Format: int32 */
            openChecks: number;
            /** Format: int32 */
            openings: number;
            /** Format: int32 */
            rooms: number;
            /** Format: date-time */
            savedAt: string;
            /** Format: uuid */
            savedBy: string | null;
            state: components["schemas"]["FloorPlanState"];
            /** Format: int32 */
            versionNo: number;
            /** Format: int32 */
            walls: number;
        };
        House: {
            archived: boolean;
            /** Format: date-time */
            archivedAt: string | null;
            /** Format: date-time */
            createdAt: string;
            dwellingType: components["schemas"]["DwellingType"];
            /** Format: uuid */
            id: string;
            levels: components["schemas"]["Level"][];
            name: string;
            project: components["schemas"]["ProjectRef"];
            stage: components["schemas"]["HouseStage"];
            /** Format: date-time */
            stageChangedAt: string;
            /** Format: int32 */
            storeys: number;
            /** Format: date-time */
            updatedAt: string;
            /** Format: int32 */
            version: number;
        };
        HouseForm: {
            dwellingType?: components["schemas"]["DwellingType"];
            name: string;
            /** Format: int32 */
            version?: number;
        };
        /** @enum {string} */
        HouseStage: "awaiting_upload" | "analysing" | "floor_plan_review" | "floor_plan_approved" | "electrical_design" | "electrical_review" | "changes_requested" | "design_approved" | "quoting" | "quote_sent" | "won" | "lost" | "on_hold" | "archived";
        HouseSummary: {
            archived: boolean;
            dwellingType: components["schemas"]["DwellingType"];
            /** Format: uuid */
            id: string;
            name: string;
            stage: components["schemas"]["HouseStage"];
            /** Format: date-time */
            updatedAt: string;
        };
        /** @description A JSON document, checked against its schema in contracts/ (e.g. floor-plan.schema.json). */
        JsonNode: {
            [key: string]: unknown;
        };
        Level: {
            /** Format: int32 */
            ceilingHeightMm: number;
            /** Format: uuid */
            currentFloorPlanVersionId: string | null;
            /** Format: uuid */
            id: string;
            name: string;
            /** Format: int32 */
            ordinal: number;
        };
        Licence: {
            /** Format: date */
            licenceEndsOn: string | null;
            /** Format: date */
            licenceStartsOn: string;
            /** Format: int32 */
            seatLimit: number;
            /** Format: int64 */
            seatsInUse: number;
            status: components["schemas"]["LicenceStatus"];
        };
        /** @enum {string} */
        LicenceStatus: "trial" | "active" | "suspended" | "closed";
        Me: {
            copy: components["schemas"]["SupabaseUser"] | null;
            email: string | null;
            /** Format: uuid */
            id: string;
            /** Format: date-time */
            tokenExpiresAt: string | null;
        };
        /** @enum {string} */
        MemberRole: "owner" | "admin" | "builder" | "electrician" | "viewer";
        Membership: {
            /** Format: uuid */
            id: string;
            licence: components["schemas"]["LicenceStatus"];
            name: string;
            role: components["schemas"]["MemberRole"];
        };
        Message: {
            message: string;
        };
        /** @enum {string} */
        Permission: "company.view" | "project.edit" | "floor-plan.edit" | "design.edit" | "rule.override" | "review.request" | "design.sign-off" | "quote.edit" | "member.manage" | "company.edit" | "licence.view" | "ownership.transfer";
        Project: {
            archived: boolean;
            /** Format: date-time */
            archivedAt: string | null;
            /** Format: date-time */
            createdAt: string;
            description: string | null;
            distributor: string | null;
            /** Format: date */
            dueOn: string | null;
            houses: components["schemas"]["HouseSummary"][];
            /** Format: uuid */
            id: string;
            /** Format: date-time */
            lastActivityAt: string;
            lotNumber: string | null;
            name: string;
            reference: string;
            site: components["schemas"]["Site"];
            status: components["schemas"]["ProjectStatus"];
            /** Format: int32 */
            supplyPhases: number;
            /** Format: int32 */
            version: number;
        };
        ProjectForm: {
            description?: string;
            distributor?: string;
            /** Format: date */
            dueOn?: string;
            lotNumber?: string;
            name: string;
            site: components["schemas"]["SiteForm"];
            status?: components["schemas"]["ProjectStatus"];
            /** Format: int32 */
            supplyPhases?: number;
            /** Format: int32 */
            version?: number;
        };
        ProjectPage: {
            items: components["schemas"]["ProjectSummary"][];
            /** Format: int32 */
            page: number;
            /** Format: int32 */
            size: number;
            /** Format: int64 */
            total: number;
        };
        ProjectRef: {
            /** Format: uuid */
            id: string;
            name: string;
            reference: string;
        };
        /** @enum {string} */
        ProjectStatus: "active" | "on_hold" | "completed" | "cancelled";
        ProjectSummary: {
            archived: boolean;
            /** Format: int64 */
            houseCount: number;
            /** Format: uuid */
            id: string;
            /** Format: date-time */
            lastActivityAt: string;
            name: string;
            reference: string;
            stages: components["schemas"]["StageCount"][];
            state: components["schemas"]["AustralianState"];
            status: components["schemas"]["ProjectStatus"];
            suburb: string | null;
        };
        RecentHouse: {
            /** Format: uuid */
            id: string;
            name: string;
            project: components["schemas"]["ProjectRef"];
            stage: components["schemas"]["HouseStage"];
            /** Format: date-time */
            updatedAt: string;
        };
        RestoreForm: {
            /** Format: int32 */
            version?: number;
        };
        SaveDraftForm: {
            document: components["schemas"]["JsonNode"];
            origin?: components["schemas"]["FloorPlanOrigin"];
            /** Format: int32 */
            version?: number;
        };
        Site: {
            postcode: string | null;
            state: components["schemas"]["AustralianState"];
            street: string | null;
            suburb: string | null;
        };
        SiteForm: {
            postcode?: string;
            state: components["schemas"]["AustralianState"];
            street?: string;
            suburb?: string;
        };
        StageCount: {
            /** Format: int64 */
            count: number;
            stage: components["schemas"]["HouseStage"];
        };
        SupabaseUser: {
            appMetadata: {
                [key: string]: unknown;
            };
            /** Format: date-time */
            bannedUntil: string | null;
            /** Format: date-time */
            copiedAt: string;
            /** Format: date-time */
            createdAt: string;
            email: string | null;
            /** Format: date-time */
            emailConfirmedAt: string | null;
            /** Format: uuid */
            id: string;
            /** Format: date-time */
            invitedAt: string | null;
            /** Format: date-time */
            lastSignInAt: string | null;
            phone: string | null;
            /** Format: date-time */
            updatedAt: string | null;
            userMetadata: {
                [key: string]: unknown;
            };
        };
        ValidationProblem: {
            errors: components["schemas"]["FieldProblem"][];
            message: string;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
};
export type $defs = Record<string, never>;
export interface operations {
    getFile: {
        parameters: {
            query?: never;
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description The file */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "image/*": string;
                };
            };
            /** @description No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `company.view`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description No such file in this company. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Message"];
                };
            };
        };
    };
    getHouse: {
        parameters: {
            query?: never;
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["House"];
                };
            };
            /** @description No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `company.view`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description No such project or house in this company. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    updateHouse: {
        parameters: {
            query?: never;
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["HouseForm"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["House"];
                };
            };
            /** @description A field is missing or not valid; `errors` names each one. Or: No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ValidationProblem"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `project.edit`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description No such project or house in this company. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Changed by someone else since `version`, or archived. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    archiveHouse: {
        parameters: {
            query?: never;
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["House"];
                };
            };
            /** @description No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `project.edit`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description No such project or house in this company. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    openFloorPlan: {
        parameters: {
            query?: never;
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["FloorPlanDocument"];
                };
            };
            /** @description No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `company.view`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description No such house in this company, or no floor plan (version) yet. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    saveFloorPlanDraft: {
        parameters: {
            query?: never;
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SaveDraftForm"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["FloorPlanDocument"];
                };
            };
            /** @description Not a valid floor plan, or a field is wrong; `errors` names each problem. Or: No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ValidationProblem"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `floor-plan.edit`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description No such house in this company, or no floor plan (version) yet. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Saved by someone else since `version`, or the house is archived. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    uploadFloorPlanImage: {
        parameters: {
            query?: {
                mmPerPx?: number;
                version?: number;
            };
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "multipart/form-data": {
                    /** Format: binary */
                    file: string;
                };
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["FloorPlanDocument"];
                };
            };
            /** @description Not a valid floor plan, or a field is wrong; `errors` names each problem. Or: No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ValidationProblem"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `floor-plan.edit`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description No such house in this company, or no floor plan (version) yet. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Saved by someone else since `version`, or the house is archived. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description The image is larger than 25 MB. */
            413: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description The analyser could not make a plan from the image; the run is kept with the reason. */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description The analyser or file storage is not answering. */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    listFloorPlanVersions: {
        parameters: {
            query?: never;
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["FloorPlanVersion"][];
                };
            };
            /** @description No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `company.view`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description No such house in this company, or no floor plan (version) yet. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    saveFloorPlanVersion: {
        parameters: {
            query?: never;
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CommitForm"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["FloorPlanVersion"];
                };
            };
            /** @description Not a valid floor plan, or a field is wrong; `errors` names each problem. Or: No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ValidationProblem"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `floor-plan.edit`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description No such house in this company, or no floor plan (version) yet. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Saved by someone else since `version`, or the house is archived. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    getFloorPlanVersion: {
        parameters: {
            query?: never;
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path: {
                id: string;
                versionNo: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["FloorPlanDocument"];
                };
            };
            /** @description No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `company.view`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description No such house in this company, or no floor plan (version) yet. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    restoreFloorPlanVersion: {
        parameters: {
            query?: never;
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path: {
                id: string;
                versionNo: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RestoreForm"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["FloorPlanDocument"];
                };
            };
            /** @description No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `floor-plan.edit`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description No such house in this company, or no floor plan (version) yet. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Saved by someone else since `version`, or the house is archived. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    restoreHouse: {
        parameters: {
            query?: never;
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["House"];
                };
            };
            /** @description No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `project.edit`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description No such project or house in this company. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Changed by someone else since `version`, or archived. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    listRecentHouses: {
        parameters: {
            query?: {
                /** @description 1 to 20. */
                size?: number;
            };
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["RecentHouse"][];
                };
            };
            /** @description No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `company.view`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    getMe: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Me"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    listMyCompanies: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Membership"][];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    getCurrentCompany: {
        parameters: {
            query?: never;
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Current"];
                };
            };
            /** @description No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    getCurrentLicence: {
        parameters: {
            query?: never;
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Licence"];
                };
            };
            /** @description No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `licence.view`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    listProjects: {
        parameters: {
            query?: {
                /** @description true: archived projects instead of current ones. */
                archived?: boolean;
                /** @description From 0. */
                page?: number;
                /** @description Words in the name, reference, street or suburb. */
                q?: string;
                /** @description 1 to 100. */
                size?: number;
                status?: components["schemas"]["ProjectStatus"];
            };
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ProjectPage"];
                };
            };
            /** @description No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `company.view`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    createProject: {
        parameters: {
            query?: never;
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ProjectForm"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Project"];
                };
            };
            /** @description A field is missing or not valid; `errors` names each one. Or: No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ValidationProblem"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `project.edit`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    getProject: {
        parameters: {
            query?: never;
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Project"];
                };
            };
            /** @description No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `company.view`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description No such project or house in this company. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    updateProject: {
        parameters: {
            query?: never;
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ProjectForm"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Project"];
                };
            };
            /** @description A field is missing or not valid; `errors` names each one. Or: No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ValidationProblem"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `project.edit`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description No such project or house in this company. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Changed by someone else since `version`, or archived. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    archiveProject: {
        parameters: {
            query?: never;
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Project"];
                };
            };
            /** @description No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `project.edit`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description No such project or house in this company. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    addHouse: {
        parameters: {
            query?: never;
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["HouseForm"];
            };
        };
        responses: {
            /** @description Created */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["House"];
                };
            };
            /** @description A field is missing or not valid; `errors` names each one. Or: No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ValidationProblem"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `project.edit`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description No such project or house in this company. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Changed by someone else since `version`, or archived. */
            409: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    restoreProject: {
        parameters: {
            query?: never;
            header?: {
                /** @description The company to act in. Needed only when the caller belongs to more than one. */
                "X-Organisation-Id"?: string;
            };
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Project"];
                };
            };
            /** @description No company chosen while the caller belongs to several, or the header is not a company id. */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description The caller is not an active member of that company, or it is closed. Or the caller's role there lacks `project.edit`. */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
            /** @description No such project or house in this company. */
            404: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    findAddresses: {
        parameters: {
            query: {
                /** @description What has been typed so far. */
                q: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AddressSuggestion"][];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Address search is off or its provider did not answer: type the address. */
            503: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ErrorMessage"];
                };
            };
        };
    };
    listDistributors: {
        parameters: {
            query?: {
                /** @description Only distributors in this state. */
                state?: components["schemas"]["AustralianState"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["Distributor"][];
                };
            };
            /** @description Not signed in, or the access token is invalid or expired. */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
}
