import { Link } from "react-router-dom";
import { EmptyState } from "../../components/states/EmptyState";
import { ErrorState } from "../../components/states/ErrorState";
import { LoadingState } from "../../components/states/LoadingState";
import { useOrganizations } from "./api";

export function OrganizationList() {
	const { data, isLoading, isError, refetch } = useOrganizations();

	if (isLoading) {
		return <LoadingState label="Loading organizations…" />;
	}

	if (isError) {
		return <ErrorState message="Could not load organizations." onRetry={() => refetch()} />;
	}

	if (!data || data.items.length === 0) {
		return (
			<EmptyState
				title="No organizations yet"
				description="Create your first organization to get started."
			/>
		);
	}

	return (
		<ul className="flex flex-col gap-2" aria-label="Organizations">
			{data.items.map((organization) => (
				<li key={organization.id}>
					<Link
						to={`/app/organizations/${organization.id}`}
						className="block rounded-lg border border-slate-gray/20 bg-pure-white p-4 shadow-sm hover:border-victory-green"
					>
						<p className="font-heading font-semibold text-navy">{organization.name}</p>
						<p className="text-sm text-slate-gray">/{organization.slug}</p>
					</Link>
				</li>
			))}
		</ul>
	);
}
