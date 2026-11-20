// Run with mongosh; see customer-data.md. Does not delete existing customers.
const customerDb = db.getSiblingDB("customer");
const customers = customerDb.getCollection("customer");
const now = new Date();
const firstnames = ["An", "Binh", "Chi", "Dung", "Giang", "Ha", "Khanh", "Linh", "Minh", "Nam"];
const lastnames = ["Nguyen", "Tran", "Le", "Pham", "Hoang", "Huynh", "Phan", "Vu", "Dang", "Bui"];

// Match V001InitializeCustomer, including soft-deleted documents.
// Existing duplicate emails cause a failure before any seed writes.
customers.createIndex({email: 1}, {unique: true, name: "uk_customer_email"});
const operations = Array.from({length: 100}, (_, index) => {
    const number = String(index + 1).padStart(3, "0");
    const email = `customer${number}@example.com`;
    return {
        updateOne: {
            filter: {email},
            update: {
                $setOnInsert: {
                    firstname: firstnames[index % firstnames.length],
                    lastname: lastnames[Math.floor(index / firstnames.length)],
                    email,
                    address: {
                        street: "Nguyen Trai",
                        houseNumber: String(index + 1),
                        zipCode: "70000"
                    },
                    createdAt: now,
                    updatedAt: now,
                    createdBy: "customer-seed",
                    updatedBy: "customer-seed",
                    deletedAt: null,
                    deletedBy: null,
                    _class: "com.duong.ecommerce.customer.entity.Customer"
                }
            },
            upsert: true
        }
    };
});
const result = customers.bulkWrite(operations, {ordered: true});
printjson({
    inserted: result.upsertedCount, existing: result.matchedCount,
    totalCustomers: customers.countDocuments({})
});
