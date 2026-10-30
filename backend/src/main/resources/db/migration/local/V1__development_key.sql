-- local 전용 키 보관. 개인키 literal은 포함하지 않고 최초 승인된 기동에서 생성한다.
CREATE SCHEMA starter_local_only;
CREATE TABLE starter_local_only."DEV_SIGNING_KEY" (
    "KEY_ID" varchar(64)
       
       
        CONSTRAINT "NN_DEV_SIGNING_KEY_KEY_ID" NOT NULL
       
       
        CONSTRAINT "PK_DEV_SIGNING_KEY" PRIMARY KEY
    , "PRIVATE_KEY_PKCS8" bytea
       
       
        CONSTRAINT "NN_DEV_SIGNING_KEY_PRIVATE_KEY_PKCS8" NOT NULL
    , "PUBLIC_KEY_SPKI" bytea
       
       
        CONSTRAINT "NN_DEV_SIGNING_KEY_PUBLIC_KEY_SPKI" NOT NULL
    , "CREATED_AT" timestamptz(3)
       
       
        CONSTRAINT "NN_DEV_SIGNING_KEY_CREATED_AT" NOT NULL
    , "ACTIVE" boolean
       
       
        CONSTRAINT "NN_DEV_SIGNING_KEY_ACTIVE" NOT NULL
);
CREATE UNIQUE INDEX "UX_DEV_SIGNING_KEY_ACTIVE"
    ON starter_local_only."DEV_SIGNING_KEY" ("ACTIVE") WHERE "ACTIVE";
