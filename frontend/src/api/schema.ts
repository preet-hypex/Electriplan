/* eslint-disable */
/**
 * Generated from contracts/openapi.json by scripts/api-types.mjs.
 * Do not edit by hand: change the API, regenerate contracts/openapi.json
 * (see contracts/README.md), then run `npm run contracts`.
 */
export type paths = {
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
        /** @enum {string} */
        AustralianState: "NSW" | "VIC" | "QLD" | "WA" | "SA" | "TAS" | "ACT" | "NT";
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
        ErrorMessage: {
            /** @description What went wrong, in words to show the user. */
            message: string;
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
        /** @enum {string} */
        Permission: "company.view" | "project.edit" | "floor-plan.edit" | "design.edit" | "rule.override" | "review.request" | "design.sign-off" | "quote.edit" | "member.manage" | "company.edit" | "licence.view" | "ownership.transfer";
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
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
};
export type $defs = Record<string, never>;
export interface operations {
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
