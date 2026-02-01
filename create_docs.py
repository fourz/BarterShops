import os

# Create docs directory
docs_dir = r"C:\tools\_PROJECTS\Ravenkaft Dev\repos\BarterShops\docs"
os.makedirs(docs_dir, exist_ok=True)

print(f"Created directory: {docs_dir}")
print(f"Directory exists: {os.path.exists(docs_dir)}")
print(f"Ready to create documentation files")
