import { useState } from "react";
import { CreateOrganizationForm } from "../features/organizations/CreateOrganizationForm";
import { OrganizationList } from "../features/organizations/OrganizationList";

export function OrganizationsPage() {
	const [showForm, setShowForm] = useState(false);

	return (
		<div className="flex flex-col gap-8">
			<div className="flex items-center justify-between">
				<h1 className="font-heading text-2xl font-bold text-navy dark:text-[#f8fafc]">Organizations</h1>
				<button
					type="button"
					onClick={() => setShowForm((prev) => !prev)}
					className="min-h-11 rounded-md bg-victory-green px-4 py-2 text-sm font-medium text-pure-white hover:opacity-90"
				>
					{showForm ? "Cancel" : "New organization"}
				</button>
			</div>

			{showForm && (
				<section aria-label="Create organization">
					<CreateOrganizationForm onCreated={() => setShowForm(false)} />
				</section>
			)}

			<section aria-label="Existing organizations">
				<OrganizationList />
			</section>
		</div>
	);
}
