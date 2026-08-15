import { NextResponse } from "next/server";
import * as Minio from 'minio';

const minioClient = new Minio.Client({
    endPoint: 'localhost',
    port: 9000,
    useSSL: false,
    accessKey: 'admin',
    secretKey: 'password123',
});

const BUCKET_NAME = 'jpoint-raw-dumps';

export async function GET() {
    try {
        const bucketExists = await minioClient.bucketExists(BUCKET_NAME);
        if(!bucketExists) {
            console.error(`[!] Bucket '${BUCKET_NAME}' does not exist.`);
            return NextResponse.json({ error: "Bucket not found" }, { status: 404 });
        }

        const objectKeys: string[] = [];
        const stream = minioClient.listObjects(BUCKET_NAME, '', true);

        await new Promise((resolve, reject)=> {
            stream.on('data', (obj) => {
                if(obj.name && obj.name.endsWith('_report.json')) {
                    objectKeys.push(obj.name);
                }
            });
            stream.on('end', resolve);
            stream.on('error', reject);
        });

        const reports: any[] = [];
        for (const key of objectKeys) {
            try {
                const dataStream = await minioClient.getObject(BUCKET_NAME, key);
                let data = '';
                for await (const chunk of dataStream) {
                    data += chunk;
                }
                reports.push(JSON.parse(data));
            } catch (parseError) {
                console.error(`[!] Failed to parse report ${key}:`, parseError);
            }
        }

        return NextResponse.json(reports);
    
    } catch (error) {
        console.error("\n[!] MINIO FETCH ERROR:");
        console.error(error);
        console.error("-------------------\n");
        return NextResponse.json({ error: "Failed to connect to MinIO" }, { status: 500 });
    } 
}