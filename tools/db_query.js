const { Client } = require('pg');

const c = new Client({
    host: 'travelbox-peru-db.postgres.database.azure.com',
    port: 5432,
    database: 'travelbox',
    user: 'tbx_admin',
    password: process.env.DB_PASS,
    ssl: { rejectUnauthorized: false }
});

async function run() {
    await c.connect();

    const query = process.argv[2];
    const result = await c.query(query);
    console.log(JSON.stringify(result.rows, null, 2));
    await c.end();
}

run().catch(e => { console.error(e.message); c.end(); process.exit(1); });
